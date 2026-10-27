# Media test files: a film (mp4) and a sound (m4a) embedded the way each program keeps them.
#  media.pptx — PowerPoint's Insert › Video/Audio: p:pic with a:videoFile/a:audioFile and p14:media, the parts in ppt/media;
#               slide 2's film starts by itself (Start: Automatically), slide 4's sound on the first click (In Click Sequence).
#  media.docx — Word's Insert › Object › Create from file: each file in an OLE package (word/embeddings/oleObject*.bin,
#               its Ole10Native stream), shown as its icon (a PNG for the film, an EMF for the sounds, as Word draws icons);
#               the third a short beep, small enough to sit in the package's mini stream.
#  media.odp  — LibreOffice's own copy of media.pptx (draw:plugin, the files in Media/).
#  media.pdf  — (androidTest/assets: MuPDF runs on a device) a Screen annotation playing the film from the file, a RichMedia
#               annotation with the sound as its asset, and a Sound annotation's own samples (8 kHz, 8 bits, unsigned).
# Run from this folder: python3 media.py (needs ffmpeg and soffice).
import os, struct, subprocess, tempfile, zipfile, shutil
here = os.path.dirname(os.path.abspath(__file__))
tmp = tempfile.mkdtemp()
def run(*a): subprocess.run(a, check=True, capture_output=True)
clip, tone, poster, icon = (os.path.join(tmp, n) for n in ("clip.mp4", "tone.m4a", "poster.png", "speaker.png"))
run("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=160x90:rate=10:duration=2", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
    "-c:v", "libx264", "-preset", "veryslow", "-crf", "40", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "16k", "-shortest", "-movflags", "+faststart", clip)
run("ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=660:duration=2", "-c:a", "aac", "-b:a", "16k", tone)
run("ffmpeg", "-y", "-i", clip, "-ss", "1", "-frames:v", "1", poster)
run("ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=0xE8EAED:size=48x48", "-frames:v", "1", icon)
run("soffice", "--headless", f"-env:UserInstallation=file://{tmp}/profile", "--convert-to", "emf", "--outdir", tmp, icon)
read = lambda p: open(p, "rb").read()
CLIP, TONE, POSTER, ICON, EMF = read(clip), read(tone), read(poster), read(icon), read(os.path.join(tmp, "speaker.emf"))
import wave, io
beep = io.BytesIO()
with wave.open(beep, "wb") as w: w.setnchannels(1); w.setsampwidth(1); w.setframerate(8000); w.writeframes(bytes(128 + (40 if (i // 9) % 2 else -40) for i in range(800)))
BEEP = beep.getvalue()

# --- OLE packages (Word's and PowerPoint's Insert › Object) ---
def cfb(streams):
    """A compound file (version 3, 512-byte sectors) holding [streams] {name: bytes}; one under 4096 bytes in the mini stream."""
    END, FREE, FATSECT = 0xFFFFFFFE, 0xFFFFFFFF, 0xFFFFFFFD
    names = list(streams); start, mini, minifat = {}, b"", []
    for n in names:
        if len(streams[n]) < 4096:
            start[n] = len(mini) // 64; k = (len(streams[n]) + 63) // 64
            minifat += [start[n] + i + 1 for i in range(k - 1)] + [END]; mini += streams[n].ljust(k * 64, b"\0")
    big = [n for n in names if n not in start]
    dirs = (len(names) + 1 + 3) // 4
    # in the file after the FAT: the directory, the mini FAT, the mini stream, the big streams
    blobs = [b"\0" * (dirs * 512), b"".join(struct.pack("<I", v) for v in minifat), mini] + [streams[n] for n in big]
    counts = [(len(b) + 511) // 512 for b in blobs]
    fats = 1
    while fats * 128 < fats + sum(counts): fats += 1
    fat, at = [FATSECT] * fats, []
    for c in counts: at.append(len(fat) if c else END); fat += [len(fat) + k + 1 for k in range(c - 1)] + ([END] if c else [])
    fat += [FREE] * (fats * 128 - len(fat))
    for i, n in enumerate(big): start[n] = at[3 + i]
    def entry(name, kind, child, first, size, right=0xFFFFFFFF):
        n = (name + "\0").encode("utf-16-le") if name else b""
        return (n.ljust(64, b"\0") + struct.pack("<HBB3I", len(n), kind, 1, 0xFFFFFFFF, right, child) + (bytes.fromhex("0C00030000000000C000000000000046") if kind == 5 else b"\0" * 16)
                + b"\0" * 20 + struct.pack("<IQ", first, size))
    # the streams as a chain of right siblings under the root
    ents = entry("Root Entry", 5, 1, at[2], len(mini)) + b"".join(entry(n, 2, 0xFFFFFFFF, start[n], len(streams[n]), i + 2 if i + 1 < len(names) else 0xFFFFFFFF) for i, n in enumerate(names))
    ents += entry("", 0, 0xFFFFFFFF, 0, 0) * (dirs * 4 - len(names) - 1)
    blobs[0] = ents
    head = (bytes.fromhex("D0CF11E0A1B11AE1") + b"\0" * 16 + struct.pack("<HHHHH", 0x3E, 3, 0xFFFE, 9, 6) + b"\0" * 6
            + struct.pack("<IIIIIIIII", 0, fats, at[0], 0, 4096, at[1], counts[1], END, 0) + b"".join(struct.pack("<I", s) for s in range(fats)) + struct.pack("<I", FREE) * (109 - fats))
    return head + b"".join(struct.pack("<I", v) for v in fat) + b"".join(b + b"\0" * (-len(b) % 512) for b in blobs)
def package(name, b):
    """Ole10Native as Windows' Packager writes it: its size, 2, the label, the source path, 0 3, the temp path, the file."""
    path = ("C:\\Users\\Example\\Videos\\" + name).encode() + b"\0"
    body = struct.pack("<H", 2) + name.encode() + b"\0" + path + struct.pack("<HHI", 0, 3, len(path)) + path + struct.pack("<I", len(b)) + b
    return cfb({"\x01Ole10Native": struct.pack("<I", len(body)) + body})
# --- media.pptx: slides.pptx with a film on slide 2, a sound on slide 4, and a beep embedded as an object on slide 1 ---
P14 = "{DAA4B4D4-6D71-4841-9C94-3DA1E1DFA6BE}"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
def pic(spid, name, tag, x, y, w, h):
    return (f'<p:pic><p:nvPicPr><p:cNvPr id="{spid}" name="{name}"><a:hlinkClick r:id="" action="ppaction://media"/></p:cNvPr><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr>'
            f'<p:nvPr><a:{tag} r:link="rId91"/><p:extLst><p:ext uri="{P14}"><p14:media xmlns:p14="http://schemas.microsoft.com/office/powerpoint/2010/main" r:embed="rId90"/></p:ext></p:extLst></p:nvPr></p:nvPicPr>'
            f'<p:blipFill><a:blip r:embed="rId92"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="{x}" y="{y}"/><a:ext cx="{w}" cy="{h}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>')
def timing(spid, node, media):
    begin = '<p:cond delay="indefinite"/><p:cond evt="onBegin" delay="0"><p:tn val="2"/></p:cond>' if node == "afterEffect" else '<p:cond delay="indefinite"/>'
    return ('<p:timing><p:tnLst><p:par><p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"><p:childTnLst><p:seq concurrent="1" nextAc="seek"><p:cTn id="2" dur="indefinite" nodeType="mainSeq"><p:childTnLst>'
            f'<p:par><p:cTn id="3" fill="hold"><p:stCondLst>{begin}</p:stCondLst><p:childTnLst><p:par><p:cTn id="4" fill="hold"><p:stCondLst><p:cond delay="0"/></p:stCondLst><p:childTnLst>'
            f'<p:par><p:cTn id="5" presetID="1" presetClass="mediacall" presetSubtype="0" fill="hold" nodeType="{node}"><p:stCondLst><p:cond delay="0"/></p:stCondLst><p:childTnLst>'
            f'<p:cmd type="call" cmd="playFrom(0.0)"><p:cBhvr><p:cTn id="6" dur="2000" fill="hold"/><p:tgtEl><p:spTgt spid="{spid}"/></p:tgtEl></p:cBhvr></p:cmd>'
            '</p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn>'
            '<p:prevCondLst><p:cond evt="onPrev" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst><p:nextCondLst><p:cond evt="onNext" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst></p:seq>'
            f'<p:{media}><p:cMediaNode vol="80000"><p:cTn id="7" fill="hold" display="0"><p:stCondLst><p:cond delay="indefinite"/></p:stCondLst></p:cTn><p:tgtEl><p:spTgt spid="{spid}"/></p:tgtEl></p:cMediaNode></p:{media}>'
            '</p:childTnLst></p:cTn></p:par></p:tnLst></p:timing>')
media = {2: ("media1.mp4", "video", "videoFile", "image91.png", pic(90, "clip", "videoFile", 2286000, 1714500, 4572000, 2571750), timing(90, "afterEffect", "video")),
         4: ("media2.m4a", "audio", "audioFile", "image92.png", pic(91, "tone", "audioFile", 4114800, 3200400, 914400, 914400), timing(91, "clickEffect", "audio"))}
out = os.path.join(here, "media.pptx")
with zipfile.ZipFile(os.path.join(here, "slides.pptx")) as zi, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zo:
    for it in zi.infolist():
        d = zi.read(it.filename)
        for n, (part, rel, _, image, shape, t) in media.items():
            if it.filename == f"ppt/slides/slide{n}.xml":
                d = d.decode().replace("</p:spTree>", shape + "</p:spTree>").replace("</p:clrMapOvr>", "</p:clrMapOvr>" + t).encode()
            if it.filename == f"ppt/slides/_rels/slide{n}.xml.rels":
                d = d.decode().replace("</Relationships>", f'<Relationship Id="rId90" Type="http://schemas.microsoft.com/office/2007/relationships/media" Target="../media/{part}"/>'
                    f'<Relationship Id="rId91" Type="{R}/{rel}" Target="../media/{part}"/><Relationship Id="rId92" Type="{R}/image" Target="../media/{image}"/></Relationships>').encode()
        if it.filename == "ppt/slides/slide1.xml":
            d = d.decode().replace("</p:spTree>", '<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="92" name="Object 91"/><p:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>'
                '<p:xfrm><a:off x="457200" y="457200"/><a:ext cx="609600" cy="609600"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/presentationml/2006/ole">'
                '<mc:AlternateContent xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"><mc:Choice xmlns:v="urn:schemas-microsoft-com:vml" Requires="v"><p:oleObj spid="_x0000_s1026" name="Packager Shell Object" showAsIcon="1" r:id="rId93" imgW="914400" imgH="914400" progId="Package"><p:embed/></p:oleObj></mc:Choice>'
                '<mc:Fallback><p:oleObj name="Packager Shell Object" showAsIcon="1" r:id="rId93" imgW="914400" imgH="914400" progId="Package"><p:embed/><p:pic><p:nvPicPr><p:cNvPr id="0" name=""/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>'
                '<p:blipFill><a:blip r:embed="rId94"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="457200" y="457200"/><a:ext cx="609600" cy="609600"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:oleObj></mc:Fallback></mc:AlternateContent>'
                '</a:graphicData></a:graphic></p:graphicFrame></p:spTree>').encode()
        if it.filename == "ppt/slides/_rels/slide1.xml.rels":
            d = d.decode().replace("</Relationships>", f'<Relationship Id="rId93" Type="{R}/oleObject" Target="../embeddings/oleObject1.bin"/><Relationship Id="rId94" Type="{R}/image" Target="../media/image93.emf"/></Relationships>').encode()
        if it.filename == "[Content_Types].xml":
            d = d.decode().replace("<Default ", '<Default Extension="mp4" ContentType="video/mp4"/><Default Extension="m4a" ContentType="audio/mp4"/><Default ', 1)
            if 'Extension="png"' not in d: d = d.replace("<Default ", '<Default Extension="png" ContentType="image/png"/><Default ', 1)
            d = d.replace("<Default ", '<Default Extension="emf" ContentType="image/x-emf"/><Default ', 1)
            d = d.replace("</Types>", '<Override PartName="/ppt/embeddings/oleObject1.bin" ContentType="application/vnd.openxmlformats-officedocument.oleObject"/></Types>')
            d = d.encode()
        zo.writestr(it, d)
    for name, b in (("media1.mp4", CLIP), ("media2.m4a", TONE), ("image91.png", POSTER), ("image92.png", ICON)): zo.writestr("ppt/media/" + name, b)
    zo.writestr("ppt/media/image93.emf", EMF); zo.writestr("ppt/embeddings/oleObject1.bin", package("beep.wav", BEEP))

# --- media.docx: three OLE packages ---
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
def obj(n, w, h):
    return (f'<w:p><w:r><w:object w:dxaOrig="{w * 20}" w:dyaOrig="{h * 20}"><v:shape id="_x0000_i10{n}" type="#_x0000_t75" style="width:{w}pt;height:{h}pt" o:ole=""><v:imagedata r:id="rIdI{n}" o:title=""/></v:shape>'
            f'<o:OLEObject Type="Embed" ProgID="Package" ShapeID="_x0000_i10{n}" DrawAspect="Icon" ObjectID="_178000000{n}" r:id="rIdO{n}"/></w:object></w:r></w:p>')
doc = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="{W}" xmlns:r="{R}" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office"><w:body>'
       '<w:p><w:r><w:t>A film and a sound, each embedded as an object.</w:t></w:r></w:p>' + obj(1, 160, 90) + obj(2, 36, 36) + obj(3, 36, 36) +
       '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>')
rels = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        f'<Relationship Id="rIdI1" Type="{R}/image" Target="media/image1.png"/><Relationship Id="rIdO1" Type="{R}/oleObject" Target="embeddings/oleObject1.bin"/>'
        f'<Relationship Id="rIdI2" Type="{R}/image" Target="media/image2.emf"/><Relationship Id="rIdO2" Type="{R}/oleObject" Target="embeddings/oleObject2.bin"/>'
        f'<Relationship Id="rIdI3" Type="{R}/image" Target="media/image2.emf"/><Relationship Id="rIdO3" Type="{R}/oleObject" Target="embeddings/oleObject3.bin"/></Relationships>')
types = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
         '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>'
         '<Default Extension="png" ContentType="image/png"/><Default Extension="emf" ContentType="image/x-emf"/><Default Extension="bin" ContentType="application/vnd.openxmlformats-officedocument.oleObject"/>'
         '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
with zipfile.ZipFile(os.path.join(here, "media.docx"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("[Content_Types].xml", types)
    z.writestr("_rels/.rels", f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{R}/officeDocument" Target="word/document.xml"/></Relationships>')
    z.writestr("word/document.xml", doc); z.writestr("word/_rels/document.xml.rels", rels)
    z.writestr("word/media/image1.png", POSTER); z.writestr("word/media/image2.emf", EMF)
    z.writestr("word/embeddings/oleObject1.bin", package("clip.mp4", CLIP)); z.writestr("word/embeddings/oleObject2.bin", package("tone.m4a", TONE))
    z.writestr("word/embeddings/oleObject3.bin", package("beep.wav", BEEP))

# --- media.pdf: an A4 page with the three ---
objs = {}
def stream(d, b): return d.encode() + f"/Length {len(b)}>>stream\n".encode() + b + b"\nendstream"
SAMPLES = bytes(128 + (40 if (i // 9) % 2 else -40) for i in range(800))
objs[1] = b"<</Type/Catalog/Pages 2 0 R>>"
objs[2] = b"<</Type/Pages/Kids[3 0 R]/Count 1>>"
objs[3] = b"<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Resources<<>>/Contents 4 0 R/Annots[5 0 R 11 0 R 14 0 R]>>"
objs[4] = stream("<<", b"")
objs[5] = b"<</Type/Annot/Subtype/Screen/Rect[100 500 420 680]/F 4/P 3 0 R/T(clip)/A 6 0 R/AP<</N 10 0 R>>>>"
objs[6] = b"<</Type/Action/S/Rendition/OP 0/AN 5 0 R/R 7 0 R>>"
objs[7] = b"<</Type/Rendition/S/MR/N(clip)/C 8 0 R>>"
objs[8] = b"<</Type/MediaClip/S/MCD/CT(video/mp4)/D 9 0 R/P<</TF(TEMPACCESS)>>>>"
objs[9] = b"<</Type/Filespec/F(clip.mp4)/UF(clip.mp4)/EF<</F 20 0 R>>>>"
objs[10] = stream("<</Type/XObject/Subtype/Form/BBox[0 0 320 180]", b"0.13 0.13 0.14 rg 0 0 320 180 re f")
objs[11] = b"<</Type/Annot/Subtype/RichMedia/Rect[100 300 148 348]/F 4/P 3 0 R/RichMediaContent<</Assets<</Names[(tone.m4a) 12 0 R]>>>>>>"
objs[12] = b"<</Type/Filespec/F(tone.m4a)/UF(tone.m4a)/EF<</F 13 0 R>>>>"
objs[13] = stream("<</Type/EmbeddedFile/Subtype/audio#2Fmp4", TONE)
objs[14] = b"<</Type/Annot/Subtype/Sound/Rect[100 200 124 224]/F 4/P 3 0 R/Name/Speaker/Sound 15 0 R>>"
objs[15] = stream("<</Type/Sound/R 8000/C 1/B 8/E/Raw", SAMPLES)
objs[20] = stream("<</Type/EmbeddedFile/Subtype/video#2Fmp4", CLIP)
pdf, at = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n"), {}
for n in sorted(objs): at[n] = len(pdf); pdf += f"{n} 0 obj\n".encode() + objs[n] + b"\nendobj\n"
xref = len(pdf); top = max(objs) + 1
pdf += f"xref\n0 {top}\n0000000000 65535 f \n".encode() + b"".join((f"{at[n]:010d} 00000 n \n" if n in at else "0000000000 65535 f \n").encode() for n in range(1, top))
pdf += f"trailer\n<</Size {top}/Root 1 0 R>>\nstartxref\n{xref}\n%%EOF\n".encode()
os.makedirs(os.path.join(here, "../../androidTest/assets"), exist_ok=True)
open(os.path.join(here, "../../androidTest/assets/media.pdf"), "wb").write(pdf)

# --- media.odp: LibreOffice's own copy of the deck ---
run("soffice", "--headless", f"-env:UserInstallation=file://{tmp}/profile", "--convert-to", "odp", "--outdir", here, out)
shutil.rmtree(tmp)
