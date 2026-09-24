#!/usr/bin/env python3
"""
Measure the face grouping against a real library, instead of arguing about it.

Every number in SYNC_PLAN.md §6o, §6u and §6x came from this script. Re-run it when
the model, the crop, the threshold or the library changes enough to be worth asking
again — the answers are library-specific and they have already been wrong once.

    python3 tools/measure-faces.py               # both devices, every measurement
    python3 tools/measure-faces.py --lines       # just the threshold table

The labels are the user's own grouping: people they have named. That makes a
"different people" pair that scores high possibly two groups that are really one
person, so the wrong-join columns are, if anything, pessimistic.

Reads, and never writes:
  computer  ~/.local/share/local-drive-desktop/library.db
  phone     a copy of photo_metadata.db, pulled with
            adb shell run-as com.kalotrapezis.drive cat databases/photo_metadata.db > phone.db
"""
import argparse
import collections
import datetime
import itertools
import os
import random
import sqlite3
import sys

import numpy as np

DESKTOP_DB = os.path.expanduser('~/.local/share/local-drive-desktop/library.db')
GENERATED = "name NOT GLOB 'Person [0-9]*'"


def norm(v):
    length = np.linalg.norm(v)
    return v / length if length else v


def load_desktop(path=DESKTOP_DB):
    """(name, embedding, taken_at) for every face of a named person."""
    if not os.path.exists(path):
        return []
    db = sqlite3.connect(path)
    rows = db.execute(f"""SELECT p.name, f.embedding, m.taken_at FROM faces f
                          JOIN people p ON p.id = f.person_id JOIN media m ON m.sha256 = f.sha256
                          WHERE f.deleted = 0 AND p.deleted = 0 AND p.{GENERATED}""").fetchall()
    return [(n, norm(np.frombuffer(e, dtype=np.float32)), t) for n, e, t in rows if len(e) == 768]


def load_phone(path):
    if not path or not os.path.exists(path):
        return []
    db = sqlite3.connect(path)
    # taken_at exists from schema v19; older copies report 0, which is its own day and shares with nothing.
    has_taken = any(c[1] == 'taken_at' for c in db.execute('PRAGMA table_info(face_samples)'))
    taken = 's.taken_at' if has_taken else '0'
    rows = db.execute(f"""SELECT g.name, s.embedding, {taken} FROM face_samples s
                          JOIN face_groups g ON g.id = s.group_id WHERE g.{GENERATED}""").fetchall()
    return [(n, norm(np.frombuffer(e, dtype=np.float32)), t) for n, e, t in rows if len(e) == 768]


def people_with_several_faces(data):
    by = collections.defaultdict(list)
    for name, vector, taken in data:
        by[name].append((vector, taken))
    return {n: v for n, v in by.items() if len(v) >= 2}


def comparisons(by):
    """Every comparison the app actually makes: a face against each person, that person's closest face.

    Returns (score, same_person, same_day) per comparison. A face is never compared against itself.
    """
    day = lambda t: datetime.datetime.fromtimestamp(t / 1000).strftime('%Y-%m-%d') if t > 0 else None
    out = []
    for who, mine in by.items():
        for i, (vector, taken) in enumerate(mine):
            for person, theirs in by.items():
                pool = [(v, t) for j, (v, t) in enumerate(theirs) if not (person == who and j == i)]
                if not pool:
                    continue
                best = max(float(vector @ v) for v, _ in pool)
                same_day = day(taken) is not None and any(day(t) == day(taken) for _, t in pool)
                out.append((best, person == who, same_day))
    return np.array(out, dtype=float) if out else np.empty((0, 3))


def lines(name, data):
    """What each threshold does: joins won against wrong joins admitted (§6u)."""
    by = people_with_several_faces(data)
    c = comparisons(by)
    if not len(c):
        return print(f"\n{name}: nothing named yet")
    score, same = c[:, 0], c[:, 1].astype(bool)
    print(f"\n{name}: {sum(len(v) for v in by.values())} faces, {len(by)} people with more than one face, {len(score)} comparisons")
    print("  line   joins of the same person   different people wrongly joined")
    for line in (0.60, 0.65, 0.68, 0.70, 0.72, 0.75, 0.80):
        print(f"  {line:.2f}        {float((score[same] >= line).mean()):6.1%}                    {float((score[~same] >= line).mean()):8.3%}")


def same_day(name, data, line=0.75):
    """Is the same day evidence about who is in the photo? (§6x)"""
    by = people_with_several_faces(data)
    c = comparisons(by)
    if not len(c):
        return
    score, same, day = c[:, 0], c[:, 1].astype(bool), c[:, 2].astype(bool)
    if not day.any():
        return print(f"\n{name}: no dates in this copy, nothing to say about days")
    print(f"\n{name}: the same day as evidence")
    print(f"  a comparison against someone who appears that same day is the same person {same[day].mean():.1%} of the time")
    print(f"  on another day                                                            {same[~day].mean():.1%}")
    print(f"  bonus   same-person joins   wrong joins (of {len(score)})")
    for bonus in (0.0, 0.05, 0.10, 0.15):
        boosted = score + np.where(day, bonus, 0)
        joined = boosted >= line
        print(f"  +{bonus:.2f}        {float(joined[same].mean()):6.1%}            {int((joined & ~same).sum()):5d}")


def nearest_versus_average(name, data):
    """Is a person better matched by their closest face or by their average? (§6u)"""
    by = people_with_several_faces(data)
    if not by:
        return
    right_near = right_cent = total = 0
    for who, mine in by.items():
        for i, (vector, _) in enumerate(mine):
            others = {n: [v for j, (v, _) in enumerate(t) if not (n == who and j == i)] for n, t in by.items()}
            others = {n: v for n, v in others.items() if v}
            near = {n: max(float(vector @ x) for x in v) for n, v in others.items()}
            cent = {n: float(vector @ norm(np.mean(v, 0))) for n, v in others.items()}
            right_near += max(near, key=near.get) == who
            right_cent += max(cent, key=cent.get) == who
            total += 1
    print(f"\n{name}: the right person is the closest one — by nearest face {right_near/total:.1%} · by the person's average {right_cent/total:.1%}")


def learned_metric(name, data, folds=4, dim=192):
    """Would a metric learned from these labels beat the plain embedding? (§6u — it did not.)

    Held out by identity, so the test is always on people the metric has never seen. Anything else measures
    how well it memorised the training people, which is not the question.
    """
    data = [(n, v) for n, v, _ in data]
    people = sorted({n for n, _ in data})
    if len(people) < folds * 2:
        return
    random.seed(7)
    order = people[:]
    random.shuffle(order)
    results = collections.defaultdict(list)
    for f in range(folds):
        test_people = set(order[f::folds])
        train = [d for d in data if d[0] not in test_people]
        test = [d for d in data if d[0] in test_people]
        if len({n for n, _ in test}) < 2:
            continue
        W = _whiten(train, dim)
        same = [(a, b) for (na, a), (nb, b) in itertools.combinations(test, 2) if na == nb]
        diff = [(a, b) for (na, a), (nb, b) in itertools.combinations(test, 2) if na != nb]
        if not same or not diff:
            continue
        for label, matrix in (('plain', None), ('learned', W)):
            score = lambda pairs: np.array([float(norm(matrix @ a) @ norm(matrix @ b)) if matrix is not None else float(a @ b) for a, b in pairs])
            s, d = score(same), score(diff)
            results[label].append(float((s >= np.quantile(d, 0.999)).mean()))
    print(f"\n{name}: same-person links joined, at a line different people reach once in a thousand")
    for label, values in results.items():
        print(f"  {label:8s} {np.mean(values):.1%}")


def _whiten(train, dim):
    by = collections.defaultdict(list)
    for n, v in train:
        by[n].append(v)
    within = np.zeros((dim, dim))
    count = 0
    for vectors in by.values():
        if len(vectors) < 2:
            continue
        M = np.stack(vectors)
        D = M - M.mean(0)
        within += D.T @ D
        count += len(vectors)
    if not count:
        return None
    within /= count
    within += np.eye(dim) * (np.trace(within) / dim) * 0.1  # ridge: small label sets are noisy
    w, V = np.linalg.eigh(within)
    return V @ np.diag(np.maximum(w, 1e-8) ** -0.5) @ V.T


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--phone', help='a copy of the phone\'s photo_metadata.db')
    ap.add_argument('--desktop', default=DESKTOP_DB)
    ap.add_argument('--lines', action='store_true', help='only the threshold table')
    args = ap.parse_args()

    libraries = [('computer', load_desktop(args.desktop)), ('phone', load_phone(args.phone))]
    libraries = [(n, d) for n, d in libraries if d]
    if not libraries:
        sys.exit('No library found. Give --phone a pulled photo_metadata.db, or open the desktop app once.')
    for name, data in libraries:
        lines(name, data)
        if args.lines:
            continue
        same_day(name, data)
        nearest_versus_average(name, data)
        learned_metric(name, data)


if __name__ == '__main__':
    main()
