import sqlite3, os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

DB_PATH    = r"D:\JOP\clientes-scraper\clientes.db"
EXCEL_PATH = r"D:\JOP\clientes-scraper\clientes.xlsx"

conn = sqlite3.connect(DB_PATH)

wb = Workbook()
ws = wb.active
ws.title = "Clientes Potenciales"

cols   = ["#", "Nombre", "Rubro", "Telefono", "Email", "Direccion", "Comuna", "Region", "Latitud", "Longitud", "URL", "Fecha"]
fill_h = PatternFill("solid", fgColor="1F4E79")
font_h = Font(bold=True, color="FFFFFF", size=11)

for c, h in enumerate(cols, 1):
    cell = ws.cell(row=1, column=c, value=h)
    cell.fill = fill_h
    cell.font = font_h
    cell.alignment = Alignment(horizontal="center", vertical="center")
ws.row_dimensions[1].height = 22

colores = {
    "Pintura Automotriz": "FFE0B2",
    "Muebleria":          "FFE0B2",
    "Mueblería":          "E8F5E9",
    "Funeraria":          "EDE7F6",
    "Barraca":            "FFF9C4",
    "Mecanico":           "E3F2FD",
    "Mecánico":           "E3F2FD",
    "Ferreteria":         "FCE4EC",
    "Ferretería":         "FCE4EC",
}

rows = conn.execute("""
    SELECT id, nombre, rubro, telefono, email, direccion, comuna, region, latitud, longitud, url, fecha
    FROM clientes ORDER BY rubro, nombre
""").fetchall()

for ri, row in enumerate(rows, 2):
    rubro = row[2] or ""
    fill  = PatternFill("solid", fgColor=colores.get(rubro, "FFFFFF"))
    for ci, val in enumerate(row, 1):
        cell = ws.cell(row=ri, column=ci, value=val)
        cell.fill = fill
        cell.alignment = Alignment(vertical="center")
        if ci == 11 and val:
            cell.font = Font(color="0563C1", underline="single")

anchos = [5, 38, 18, 15, 28, 30, 18, 18, 10, 10, 45, 16]
for i, w in enumerate(anchos, 1):
    ws.column_dimensions[get_column_letter(i)].width = w
ws.freeze_panes = "A2"
ws.auto_filter.ref = f"A1:{get_column_letter(len(cols))}1"

# Hoja resumen
ws2 = wb.create_sheet("Resumen")
ws2.append(["Rubro", "Total", "Con telefono", "Con email", "Con coordenadas"])
for c in range(1, 6):
    ws2.cell(row=1, column=c).font = Font(bold=True)
total_g = 0
for r in conn.execute("""
    SELECT rubro, COUNT(*),
           SUM(CASE WHEN telefono IS NOT NULL THEN 1 ELSE 0 END),
           SUM(CASE WHEN email IS NOT NULL THEN 1 ELSE 0 END),
           SUM(CASE WHEN latitud IS NOT NULL THEN 1 ELSE 0 END)
    FROM clientes GROUP BY rubro ORDER BY COUNT(*) DESC
"""):
    ws2.append(list(r))
    total_g += r[1]
ws2.append(["TOTAL", total_g, "", "", ""])
ws2.cell(row=ws2.max_row, column=1).font = Font(bold=True)
ws2.cell(row=ws2.max_row, column=2).font = Font(bold=True)
for col in ["A","B","C","D","E"]:
    ws2.column_dimensions[col].width = 20

wb.save(EXCEL_PATH)
print(f"Excel guardado: {EXCEL_PATH}")

# Resumen en consola
print()
for r in conn.execute("""
    SELECT rubro, COUNT(*),
           SUM(CASE WHEN telefono IS NOT NULL THEN 1 ELSE 0 END),
           SUM(CASE WHEN latitud IS NOT NULL THEN 1 ELSE 0 END)
    FROM clientes GROUP BY rubro ORDER BY COUNT(*) DESC
"""):
    print(f"  {r[0]:22}  {r[1]:5} negocios | {r[2]} tel | {r[3]} coords")
total = conn.execute("SELECT COUNT(*) FROM clientes").fetchone()[0]
coords = conn.execute("SELECT COUNT(*) FROM clientes WHERE latitud IS NOT NULL").fetchone()[0]
print(f"\n  TOTAL: {total} negocios | {coords} con coordenadas GPS")
conn.close()
