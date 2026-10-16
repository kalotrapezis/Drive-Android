# formulas.xlsx: data in A1:E12 and Data!A1:B5, formulas in G (no values: LibreOffice works them out on converting).
import sys, datetime
from openpyxl import Workbook
out = sys.argv[1]
wb = Workbook(); ws = wb.active; ws.title = "Calc"
data = [
    ["Name", "Score", "Town", "Date", "Ok"],
    ["Αλεξίου", 12.5, "Αθήνα", datetime.date(2026, 3, 15), True],
    ["Βασιλείου", 9, "Πάτρα", datetime.date(2025, 12, 31), False],
    ["Γεωργίου", None, "Αθήνα", datetime.date(2026, 1, 1), True],
    ["Δήμου", 15.25, "Λάρισα", datetime.date(2024, 2, 29), True],
    ["Ελένη", "7", "Πάτρα", datetime.date(2026, 10, 16), False],
    ["Ζαχαρίου", -3, "αθήνα", datetime.date(2000, 1, 1), True],
    ["Θεοδώρου", 0, "Βόλος", datetime.date(1999, 12, 31), False],
    ["apple pie", 100, "Βόλος", datetime.date(2026, 2, 28), True],
    ["Banana", 2.675, "", datetime.date(2026, 6, 30), False],
    ["cherry", 1e-3, "Αθήνα", datetime.date(2026, 7, 1), True],
]
for row in data: ws.append(row)
d = wb.create_sheet("Data"); 
for row in [["k", "v"], ["a", 1], ["b", 2], ["c", 3], ["d", 4]]: d.append(row)
formulas = [
 "SUM(B2:B11)", "SUM(B:B)", "AVERAGE(B2:B11)", "COUNT(B2:B11)", "COUNTA(A1:E11)", "COUNTBLANK(B2:B11)", "MIN(B2:B11)", "MAX(B2:B11)",
 "MEDIAN(B2:B11)", "PRODUCT(B2,B3,B5)", "SUM(B2,3)", "B2+B6", "B6*2", "B4+1", "A2&\" \"&C2", "B2/B8", "B2^2", "-B7^2", "10%", "B2>B3",
 "A2=\"αλεξίου\"", "A2<A3", "1/3", "ROUND(B11*1000+0.5,0)", "ROUND(2.675,2)", "ROUND(-2.5,0)", "ROUNDUP(1.21,1)", "ROUNDDOWN(-1.29,1)", "ROUND(1234.5678,-2)",
 "INT(-2.5)", "TRUNC(-2.5)", "MOD(10,3)", "MOD(-10,3)", "MOD(10,-3)", "ABS(B7)", "SQRT(16)", "POWER(2,10)", "SIGN(B7)", "CEILING(2.1,1)", "FLOOR(2.9,1)",
 "IF(B2>10,\"big\",\"small\")", "IF(B4,\"yes\",\"no\")", "IF(B3>100,\"x\")", "IFERROR(B2/B8,\"div\")", "IFERROR(B2,\"x\")", "AND(E2,E4)", "OR(E3,E8)", "NOT(E2)",
 "AND(B2>1,C2=\"Αθήνα\")", "SUMIF(C2:C11,\"Αθήνα\",B2:B11)", "SUMIF(B2:B11,\">5\")", "COUNTIF(C2:C11,\"Αθήνα\")", "COUNTIF(B2:B11,\">=9\")", "COUNTIF(C2:C11,\"Β*\")",
 "COUNTIF(C2:C11,\"<>Αθήνα\")", "COUNTIF(B2:B11,\"\")", "AVERAGEIF(C2:C11,\"Πάτρα\",B2:B11)", "SUMIFS(B2:B11,C2:C11,\"Αθήνα\",E2:E11,TRUE)", "COUNTIFS(C2:C11,\"Βόλος\",B2:B11,\">50\")",
 "AVERAGEIFS(B2:B11,E2:E11,FALSE)", "MAXIFS(B2:B11,C2:C11,\"Αθήνα\")", "MINIFS(B2:B11,C2:C11,\"Βόλος\")",
 "VLOOKUP(\"Δήμου\",A2:C11,3,FALSE)", "VLOOKUP(\"δήμου\",A2:B11,2,0)", "VLOOKUP(\"zz\",A2:B11,2,FALSE)", "VLOOKUP(2.5,Data!B2:B5,1,TRUE)", "VLOOKUP(\"c\",Data!A2:B5,2,FALSE)",
 "HLOOKUP(\"Town\",A1:E3,2,FALSE)", "MATCH(\"Λάρισα\",C2:C11,0)", "MATCH(3,Data!B2:B5,1)", "MATCH(\"b*\",Data!A2:A5,0)", "INDEX(A2:C11,4,3)", "INDEX(Data!B2:B5,2)",
 "INDEX(Data!A2:B5,MATCH(3,Data!B2:B5,0),1)", "CHOOSE(2,\"a\",\"b\",\"c\")", "RANK(B5,B2:B11)", "RANK(B5,B2:B11,1)", "LARGE(B2:B11,2)", "SMALL(B2:B11,2)",
 "CONCATENATE(A2,\"-\",B2)", "LEN(A2)", "UPPER(A9)", "LOWER(A3)", "PROPER(\"hello big world\")", "TRIM(\"  a   b  \")", "LEFT(A2,3)", "RIGHT(A2,2)", "MID(A9,7,3)",
 "SUBSTITUTE(A9,\"pie\",\"tart\")", "REPT(\"ab\",3)", "FIND(\"p\",A9)", "SEARCH(\"P\",A9)", "VALUE(\"12\")", "EXACT(\"a\",\"A\")", "TEXT(B2,\"0.00\")", "TEXT(D2,\"dd/mm/yyyy\")",
 "DATE(2026,2,30)", "DATE(2026,13,1)", "YEAR(D2)", "MONTH(D2)", "DAY(D5)", "WEEKDAY(D2)", "WEEKDAY(D2,2)", "D2-D3", "EDATE(D2,1)", "EOMONTH(D2,0)", "DAYS(D2,D3)",
 "ISBLANK(B4)", "ISBLANK(B2)", "ISNUMBER(B6)", "ISNUMBER(B2)", "ISTEXT(B6)", "ISERROR(B2/B8)", "ISNA(VLOOKUP(\"zz\",A2:B11,2,FALSE))", "IFNA(VLOOKUP(\"zz\",A2:B11,2,FALSE),\"none\")",
 "ROWS(A2:C11)", "COLUMNS(A2:C11)", "SUM(Data!B2:B5)", "Data!B3*10", "STDEV(B2:B11)", "VAR(B2:B11)", "STDEVP(B2:B11)", "TEXTJOIN(\", \",TRUE,C2:C5)",
 "SUM(B2:B11)/COUNT(B2:B11)", "(B2+B3)*(B5-B7)/2", "\"a\"&1.5", "B2&\"\"", "XLOOKUP(\"Λάρισα\",C2:C11,A2:A11)", "MROUND(10,3)",
]
# Excel's newer functions are written with their prefix, as Excel writes them
newer = ["MAXIFS", "MINIFS", "DAYS", "IFNA", "TEXTJOIN", "XLOOKUP", "CONCAT"]
import re
formulas = [re.sub(r"\b(" + "|".join(newer) + r")\(", r"_xlfn.\1(", f) for f in formulas]
for i, f in enumerate(formulas, start=1):
    ws.cell(row=i, column=7, value="=" + f)
wb.save(out)
