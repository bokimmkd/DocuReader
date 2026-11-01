from pathlib import Path
from docx import Document
from openpyxl import Workbook
from PIL import Image
root=Path('app/src/androidTest/assets');root.mkdir(parents=True,exist_ok=True)
im=Image.new('RGB',(80,40),'#087cdb'); im.save(root/'sample.png')
doc=Document();doc.add_heading('WORD_START Кирилица',0)
doc.add_picture(str(root/'sample.png'))
t=doc.add_table(rows=3,cols=2);t.style='Table Grid'
for i,row in enumerate(t.rows):
    row.cells[0].text='Cell '+str(i);row.cells[1].text='Табела '+str(i)
for i in range(35):doc.add_paragraph('Word content '+str(i)+' with Unicode: Македонски текст')
doc.add_paragraph('Math ∈ ∉ ∪ ∩ ⊂ ≤ ≥ ℕ π Ω ∞');doc.add_page_break();doc.add_paragraph('WORD_END Крај')
doc.save(root/'word.docx')
wb=Workbook();ws=wb.active;ws.title='Invoices'
ws.append(['EXCEL_START','Qty','Price','Total','Status','Кирилица'])
for i in range(2,151):ws.append(['Item '+str(i),i,2.50,i*2.50,'Paid','Табела'])
ws['A150']='EXCEL_END';ws['C2'].number_format='$0.00'
for col in 'ABCDEF':ws.column_dimensions[col].width=16
wb.create_sheet('Do not print')['A1']='OTHER_SHEET_NOT_SELECTED'
wb.create_sheet('Too large')['A801']='TRUNCATED_END'
wb.save(root/'excel.xlsx')
# Regression: a real wide invoice-like worksheet must fit all 12 columns
# in both portrait and landscape PDFs without falling back to Print.
wide=Workbook(); invoice=wide.active; invoice.title='Wide invoice'
from openpyxl.utils import get_column_letter
for col in range(1,13):
    letter=get_column_letter(col)
    invoice.column_dimensions[letter].width=21
    invoice.cell(1,col,'WIDE_START' if col==1 else ('COL_12_LAST' if col==12 else 'COLUMN_'+str(col)))
for row in range(2,37):
    for col in range(1,13):
        invoice.cell(row,col,'R'+str(row)+'C'+str(col))
invoice['A37']='WIDE_END'
wide.save(root/'wide-excel.xlsx')
# Unequal columns and merged invoice headers reproduce the layout lost when
# print CSS hid the only row carrying worksheet column widths.
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.drawing.image import Image as SheetImage
invoice_book=Workbook(); invoice=invoice_book.active; invoice.title='Invoice'
widths=[6,18,32,22,15,9,14,16,10,12,12,17]
for col,width in enumerate(widths,1):invoice.column_dimensions[get_column_letter(col)].width=width
invoice.merge_cells('A1:F1');invoice['A1']='INVOICE_LAYOUT_START';invoice['A1'].font=Font(size=18,bold=True)
invoice.merge_cells('G1:L1');invoice['G1']='INVOICE'
invoice.merge_cells('A3:E3');invoice['A3']='Bill to: Example customer'
invoice.merge_cells('F3:H3');invoice['F3']='Invoice number 2026-001'
invoice.merge_cells('I3:L3');invoice['I3']='Date 2026-11-01'
headers=['NO','ITEM','MODEL','DESCRIPTION','PAGE YIELD','QTY','UNIT PRICE','AMOUNT','CTN QTY','TOTAL CTN','SIZE','RIGHTMOST_CBM']
for col,value in enumerate(headers,1):
    c=invoice.cell(6,col,value);c.font=Font(color='FFFFFF',bold=True);c.fill=PatternFill('solid',fgColor='315A80')
for row in range(7,39):
    values=[row-6,'Toner cartridge','Model-'+str(row)+'-Premium','Compatible toner','1500 pages',100,'2.62','262.00',20,5,'0.0134','END_'+str(row)]
    for col,value in enumerate(values,1):invoice.cell(row,col,value)
    invoice.row_dimensions[row].height=20
invoice.merge_cells('A39:E39');invoice['A39']='SUBTOTAL';invoice['H39']='8384.00';invoice['L39']='TOTAL_RIGHT'
invoice.merge_cells('A41:B41');invoice['A41']='Maintenance kit'
pic=SheetImage(root/'sample.png');invoice.add_image(pic,'C41');invoice.row_dimensions[41].height=50
invoice.merge_cells('A43:L43');invoice['A43']='INVOICE_LAYOUT_END'
invoice_book.save(root/'layout-invoice.xlsx')
(root/'text.txt').write_text('TXT_START <tag> Кирилица\n'+'\n'.join('Line '+str(i)+' Македонски текст' for i in range(150))+'\nMath ∈ ∉ ∪ ∩ ⊂ ≤ ≥ ℕ π Ω ∞\nTXT_END Крај',encoding='utf-8')
print('Print fixtures created')
