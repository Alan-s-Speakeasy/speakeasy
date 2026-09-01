import {GradingSettings} from './grading-settings';
import {createZipBlob} from './zip-store';

export interface SummaryExcelAnswer {
  verdict: 'correct' | 'partial' | 'incorrect' | 'timeout' | null
  timeMs: number | null
  fastBonusPoints: number
}

export interface SummaryExcelRow {
  username: string
  answers: SummaryExcelAnswer[]
  avgMs: number | null
  fastestMs: number | null
  fastestQuestion: number | null
  longestMs: number | null
  longestQuestion: number | null
  grade: number
}

const NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
const REL_NS = 'http://schemas.openxmlformats.org/package/2006/relationships';
const DOC_REL_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';

export function createSummaryExcelBlob(
  questionCount: number,
  rows: SummaryExcelRow[],
  settings: GradingSettings
): Blob {
  const zip = createZipBlob([
    {name: '[Content_Types].xml', content: contentTypesXml()},
    {name: '_rels/.rels', content: rootRelsXml()},
    {name: 'xl/workbook.xml', content: workbookXml()},
    {name: 'xl/_rels/workbook.xml.rels', content: workbookRelsXml()},
    {name: 'xl/styles.xml', content: stylesXml()},
    {name: 'xl/worksheets/sheet1.xml', content: summarySheetXml(questionCount, rows)},
    {name: 'xl/worksheets/sheet2.xml', content: scoringSheetXml(settings)}
  ]);
  return new Blob([zip], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  });
}

function contentTypesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;
}

function rootRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="${REL_NS}">
  <Relationship Id="rId1" Type="${DOC_REL_NS}/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;
}

function workbookXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="${NS}" xmlns:r="${DOC_REL_NS}">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Scoring" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>`;
}

function workbookRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="${REL_NS}">
  <Relationship Id="rId1" Type="${DOC_REL_NS}/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="${DOC_REL_NS}/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="${DOC_REL_NS}/styles" Target="styles.xml"/>
</Relationships>`;
}

function stylesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="${NS}">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="0.000"/>
    <numFmt numFmtId="165" formatCode="0.0"/>
  </numFmts>
  <fonts count="3">
    <font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="11"/><color rgb="FF006100"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E79"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFEB9C"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFB0B0B0"/></left>
      <right style="thin"><color rgb="FFB0B0B0"/></right>
      <top style="thin"><color rgb="FFB0B0B0"/></top>
      <bottom style="thin"><color rgb="FFB0B0B0"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="8">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
    <xf numFmtId="164" fontId="2" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center"/>
    </xf>
  </cellXfs>
</styleSheet>`;
}

function summarySheetXml(questionCount: number, rows: SummaryExcelRow[]): string {
  const headers = ['Chat'];
  for (let i = 1; i <= questionCount; i++) {
    headers.push(`Q${i} result`, `Q${i} time (s)`, `Q${i} bonus`);
  }
  headers.push('Average (s)', 'Fastest (s)', 'Fastest question', 'Longest (s)', 'Longest question', 'Grade');

  const sheetRows: string[] = [rowXml(1, headers.map((header, index) => stringCell(colName(index), 1, header, 1)))];
  rows.forEach((row, rowIndex) => {
    const r = rowIndex + 2;
    const cells: string[] = [stringCell('A', r, row.username, 0)];
    let col = 1;
    row.answers.forEach(answer => {
      cells.push(stringCell(colName(col), r, answer.verdict ?? '', verdictStyle(answer.verdict)));
      col += 1;
      if (answer.timeMs == null) {
        cells.push(stringCell(colName(col), r, '', 0));
      } else {
        cells.push(numberCell(colName(col), r, answer.timeMs / 1000, 2));
      }
      col += 1;
      if (answer.fastBonusPoints > 0) {
        cells.push(numberCell(colName(col), r, answer.fastBonusPoints, 7));
      } else {
        cells.push(stringCell(colName(col), r, '', 0));
      }
      col += 1;
    });
    cells.push(timeOrEmpty(colName(col), r, row.avgMs));
    col += 1;
    cells.push(timeOrEmpty(colName(col), r, row.fastestMs));
    col += 1;
    if (row.fastestQuestion == null) {
      cells.push(stringCell(colName(col), r, '', 0));
    } else {
      cells.push(numberCell(colName(col), r, row.fastestQuestion, 0));
    }
    col += 1;
    cells.push(timeOrEmpty(colName(col), r, row.longestMs));
    col += 1;
    if (row.longestQuestion == null) {
      cells.push(stringCell(colName(col), r, '', 0));
    } else {
      cells.push(numberCell(colName(col), r, row.longestQuestion, 0));
    }
    col += 1;
    cells.push(numberCell(colName(col), r, row.grade, 3));
    sheetRows.push(rowXml(r, cells));
  });

  const lastCol = colName(headers.length - 1);
  const lastRow = Math.max(rows.length + 1, 1);
  const cols = headers.map((_, index) => {
    const width = index === 0 ? 18 : 14;
    return `<col min="${index + 1}" max="${index + 1}" width="${width}" customWidth="1"/>`;
  }).join('');

  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="${NS}" xmlns:r="${DOC_REL_NS}">
  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="18"/>
  <cols>${cols}</cols>
  <sheetData>${sheetRows.join('')}</sheetData>
  <autoFilter ref="A1:${lastCol}${lastRow}"/>
</worksheet>`;
}

function scoringSheetXml(settings: GradingSettings): string {
  const lines = [
    ['Rule', 'Points'],
    ['Correct answer', String(settings.correctPoints)],
    ['Partial answer', String(settings.partialPoints)],
    ...(settings.speedBonusMode === 'dynamic'
      ? [
        [`Speed bonus (dynamic): full until ${settings.fastBonusSeconds}s`, `+${settings.fastBonusPoints}`],
        [`Speed bonus fades to 0 at ${settings.fastBonusZeroSeconds}s`, 'linear']
      ]
      : [[`Speed bonus (manual): if under ${settings.fastBonusSeconds}s`, `+${settings.fastBonusPoints}`]]),
    [
      'Speed bonus applies to',
      settings.speedBonusCorrectOnly ? 'Correct answers only' : 'Any in-time answer'
    ],
    [`No answer within ${settings.timeoutSeconds} seconds`, '0'],
    ['Incorrect', '0']
  ];
  const sheetRows = lines.map((line, index) => {
    const r = index + 1;
    const style = r === 1 ? 1 : 0;
    return rowXml(r, [
      stringCell('A', r, line[0] ?? '', style),
      stringCell('B', r, line[1] ?? '', style)
    ]);
  });
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="${NS}">
  <sheetFormatPr defaultRowHeight="18"/>
  <cols>
    <col min="1" max="1" width="56" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
  </cols>
  <sheetData>${sheetRows.join('')}</sheetData>
</worksheet>`;
}

function timeOrEmpty(col: string, row: number, ms: number | null): string {
  if (ms == null) {
    return stringCell(col, row, '', 0);
  }
  return numberCell(col, row, ms / 1000, 2);
}

function verdictStyle(verdict: SummaryExcelAnswer['verdict']): number {
  if (verdict === 'correct') {
    return 4;
  }
  if (verdict === 'partial') {
    return 7;
  }
  if (verdict === 'incorrect') {
    return 5;
  }
  if (verdict === 'timeout') {
    return 6;
  }
  return 0;
}

function rowXml(row: number, cells: string[]): string {
  return `<row r="${row}" ht="20" customHeight="1">${cells.join('')}</row>`;
}

function stringCell(col: string, row: number, value: string, style: number): string {
  return `<c r="${col}${row}" t="inlineStr" s="${style}"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>`;
}

function numberCell(col: string, row: number, value: number, style: number): string {
  return `<c r="${col}${row}" t="n" s="${style}"><v>${value}</v></c>`;
}

function colName(index: number): string {
  let n = index + 1;
  let name = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    name = String.fromCharCode(65 + rem) + name;
    n = Math.floor((n - 1) / 26);
  }
  return name;
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
