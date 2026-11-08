/* W Reader offline Office UI. No external URLs, analytics, storage or editing. */
(function() {
'use strict';
const state = { zoom: 0.5, workbook: null, images: [], truncated: false };
window.officeReady = false;
const status = document.getElementById('status');
const content = document.getElementById('content');
const sheetbar = document.getElementById('sheetbar');
const sheetSelect = document.getElementById('sheet');
const notice = document.getElementById('notice');
const MAX_SHEET_ROWS = 800, MAX_SHEET_COLS = 90;

function message(value) { status.textContent = value; status.hidden = false; }
function info(value) { notice.hidden = false; notice.textContent = value; }
function clearMessage() { status.hidden = true; }
const viewport = document.getElementById('viewport');
const sheetViews = new Map();
let activeSheet = null;
function visibleWordPage() {
    const pages = Array.from(content.querySelectorAll('.docx-wrapper > section.docx'));
    const frame = {top:0, bottom:window.innerHeight};
    return pages.reduce((best, page) => {
        const rect = page.getBoundingClientRect();
        const visible = Math.max(0, Math.min(rect.bottom, frame.bottom) - Math.max(rect.top, frame.top));
        return visible > best.visible ? {page, visible} : best;
    }, {page: pages[0], visible: -1}).page;
}
function zoomTarget() { return state.workbook ? content : visibleWordPage(); }
function targetZoom(node) { return Number(node && node.style.zoom) || 1; }
function fitZoom(node) {
    const width = state.workbook ? content.querySelector('table.sheet')?.offsetWidth : node?.offsetWidth;
    return Math.max(0.05, Math.min(1, (viewport.clientWidth - 16) / Math.max(1, width || 1)));
}
function applyZoom(node, value) {
    if (!node) return;
    state.zoom = Math.max(0.05, Math.min(8, Number(value) || 1));
    node.style.zoom = String(state.zoom);
}
window.getOfficeZoom = function() { return targetZoom(zoomTarget()); };
window.setOfficeZoom = function(value) { applyZoom(zoomTarget(), value); };
window.changeOfficeZoom = function(delta) {
    const node = zoomTarget(); applyZoom(node, targetZoom(node) + delta);
};
window.fitOfficePage = function() {
    const node = zoomTarget(); applyZoom(node, fitZoom(node)); viewport.scrollLeft = 0;
    if (node && !state.workbook) node.scrollIntoView({block:'start', inline:'start'});
};
// Native WebView zoom scales the whole document. Handle pinch on the touched
// Word page/current worksheet instead; single-finger scrolling stays native.
let pinch = null;
const distance = touches => Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY);
viewport.addEventListener('touchstart', event => {
    if (event.touches.length !== 2) return;
    const node = state.workbook ? content : event.target.closest('section.docx') || visibleWordPage();
    pinch = node ? {node, distance:distance(event.touches), zoom:targetZoom(node)} : null;
    if (pinch) event.preventDefault();
}, {passive:false});
viewport.addEventListener('touchmove', event => {
    if (!pinch || event.touches.length !== 2) return;
    event.preventDefault();
    const node = pinch.node, before = node.getBoundingClientRect();
    const x = (event.touches[0].clientX + event.touches[1].clientX) / 2;
    const y = (event.touches[0].clientY + event.touches[1].clientY) / 2;
    const old = targetZoom(node);
    applyZoom(node, pinch.zoom * distance(event.touches) / Math.max(1, pinch.distance));
    const after = node.getBoundingClientRect(), ratio = targetZoom(node) / old;
    viewport.scrollLeft += after.left + (x - before.left) * ratio - x;
    window.scrollBy(0, after.top + (y - before.top) * ratio - y);
}, {passive:false});
function endPinch(event) { if (event.touches.length < 2) pinch = null; }
viewport.addEventListener('touchend', endPinch);
viewport.addEventListener('touchcancel', () => { pinch = null; });

function cellColor(color) {
    if (!color) return null;
    if (color.argb && /^[A-F\d]{8}$/i.test(color.argb)) return '#' + color.argb.substring(2);
    return null;
}
function styledCell(td, cell) {
    const style = cell.style || {};
    const font = style.font || {};
    const fill = style.fill || {};
    const bg = cellColor(fill.fgColor);
    const fg = cellColor(font.color);
    if (bg) td.style.backgroundColor = bg;
    if (fg) td.style.color = fg;
    if (font.bold) td.style.fontWeight = 'bold';
    if (font.italic) td.style.fontStyle = 'italic';
    if (font.underline) td.style.textDecoration = 'underline';
    if (font.size) td.style.fontSize = Math.min(24, Math.max(8, font.size)) + 'px';
    if (style.alignment) {
        if (style.alignment.horizontal) td.style.textAlign = style.alignment.horizontal;
        if (style.alignment.wrapText) td.style.whiteSpace = 'pre-wrap';
        if (style.alignment.vertical) td.style.verticalAlign =
            style.alignment.vertical === 'middle' ? 'middle' : style.alignment.vertical;
    }
    if (style.border) {
        for (const [side,css] of [['top','borderTop'],['left','borderLeft'],['right','borderRight'],['bottom','borderBottom']]) {
            const edge=style.border[side];
            if(edge && edge.style) td.style[css] = '1px solid ' + (cellColor(edge.color) || '#8495ab');
        }
    }
}
function formatCell(cell) {
    const value = cell.value;
    if (value == null) return '';
    if (value instanceof Date) return value.toLocaleDateString();
    if (typeof value === 'object') {
        if ('result' in value) {
            if (value.result === undefined || value.result === null) {
                return '= ' + (value.formula || value.sharedFormula || 'Formula (not calculated)');
            }
            return String(value.result);
        }
        if (value.richText) return value.richText.map(part => part.text || '').join('');
        if (value.text) return String(value.text);
        if (value.hyperlink) return String(value.text || value.hyperlink);
        if (value.error) return String(value.error);
        return '';
    }
    if (typeof value === 'number') {
        const fmt = String(cell.numFmt || '').replace(/"[^"]*"/g,'');
        if (fmt.includes('%')) {
            const count=(fmt.split('.')[1] || '').match(/0/g);
            return (value*100).toFixed(count?Math.min(count.length,4):0)+'%';
        }
        const money = fmt.match(/[€$£¥]/);
        const decimals=(fmt.split('.')[1] || '').match(/0/g);
        if (money) return money[0] + value.toLocaleString(undefined, {minimumFractionDigits:decimals?Math.min(decimals.length,3):0,maximumFractionDigits:decimals?Math.min(decimals.length,3):2});
        if (fmt.includes('#,##0')) return value.toLocaleString(undefined,{maximumFractionDigits:decimals?Math.min(decimals.length,3):0});
    }
    return String(value);
}
function indexCol(col) {
    let n=0; for (const ch of String(col).toUpperCase()) n=n*26+(ch.charCodeAt(0)-64);return n;
}
function bounds(address) {
    const parts=String(address).split(':');
    function one(cell) {
        const m=cell.match(/^\$?([A-Z]+)\$?(\d+)$/i);
        return m ? [Number(m[2]),indexCol(m[1])] : [0,0];
    }
    const a=one(parts[0]),b=one(parts[1] || parts[0]);return {r:a[0],c:a[1],rows:b[0]-a[0]+1,cols:b[1]-a[1]+1};
}
function renderSheet(ws) {
    if (activeSheet != null) sheetViews.set(activeSheet, {zoom:targetZoom(content), x:viewport.scrollLeft, y:window.scrollY});
    activeSheet = ws.id;
    content.style.zoom='1';
    content.textContent='';
    state.truncated = ws.rowCount > MAX_SHEET_ROWS || ws.columnCount > MAX_SHEET_COLS;
    const extent = ws.dimensions || {top:1,left:1,bottom:ws.rowCount,right:ws.columnCount};
    const nRows=Math.min(Math.max(ws.rowCount,1),MAX_SHEET_ROWS);
    const nCols=Math.min(Math.max(ws.columnCount,1),MAX_SHEET_COLS);
    if (ws.rowCount>MAX_SHEET_ROWS || ws.columnCount>MAX_SHEET_COLS) info('Large sheet: showing first '+nRows+' rows and '+nCols+' columns, to limit memory use.');
    const merges=new Map(),hidden=new Set();
    for (const ref of (ws.model.merges || [])) {
        const v=bounds(ref);
        if(!v.r||!v.c)continue;
        merges.set(v.r+':'+v.c,v);
        for(let r=v.r;r<v.r+v.rows;r++)for(let c=v.c;c<v.c+v.cols;c++){
            if(r!==v.r || c!==v.c)hidden.add(r+':'+c);
        }
    }
    const table=document.createElement('table');table.className='sheet';
    const thead=document.createElement('thead'),heading=document.createElement('tr');
    const corner=document.createElement('th');corner.className='rowhead';heading.appendChild(corner);
    for(let c=1;c<=nCols;c++){
        let n=c, label='';
        do {n--;label=String.fromCharCode(65+n%26)+label;n=Math.floor(n/26);} while(n>0);
        const th=document.createElement('th');th.textContent=label;
        const width=ws.getColumn(c).width;
        if(width&&Number.isFinite(width))th.style.minWidth=Math.max(60,Math.min(width*8,320))+'px';
        heading.appendChild(th);
    }
    thead.appendChild(heading);table.appendChild(thead);
    const tbody=document.createElement('tbody');
    for(let r=1;r<=nRows;r++){
        const row=document.createElement('tr');
        const th=document.createElement('th');th.className='rowhead';th.textContent=r;row.appendChild(th);
        const worksheetRow=ws.getRow(r);
        if(worksheetRow.height)row.style.height=Math.min(worksheetRow.height*1.35,250)+'px';
        for(let c=1;c<=nCols;c++){
            const key=r+':'+c;if(hidden.has(key))continue;
            const cell=ws.getCell(r,c),td=document.createElement('td');
            td.dataset.sheetColumn=String(c);
            td.textContent=formatCell(cell);styledCell(td,cell);
            if(merges.has(key)){
                const merged=merges.get(key);
                td.rowSpan=Math.min(merged.rows,nRows-r+1);
                td.colSpan=Math.min(merged.cols,nCols-c+1);
            }
            row.appendChild(td);
        }
        tbody.appendChild(row);
    }
    table.appendChild(tbody);content.appendChild(table);
    try {
        // Images are anchored to cells; display image content where worksheet drawing data is accessible.
        const drawings=typeof ws.getImages==='function'?ws.getImages():[];
        for (const drawing of drawings) {
            const im=state.workbook.getImage(drawing.imageId);
            if (!im || !im.buffer || !drawing.range || !drawing.range.tl) continue;
            const cellRow=Math.floor(drawing.range.tl.nativeRow)+1;
            const cellCol=Math.floor(drawing.range.tl.nativeCol)+1;
            const tr=tbody.children[cellRow-1];
            const td=tr&&Array.from(tr.querySelectorAll('td')).find(cell=>{
                const start=Number(cell.dataset.sheetColumn);return cellCol>=start&&cellCol<start+cell.colSpan;
            });
            if (!td)continue;
            const bytes=new Uint8Array(im.buffer);
            const url=URL.createObjectURL(new Blob([bytes],{type:'image/'+(im.extension==='jpg'?'jpeg':im.extension||'png')}));
            state.images.push(url);
            const img=document.createElement('img');img.src=url;img.alt='Worksheet image';
            td.appendChild(img);
        }
    } catch (_) { /* Unsupported image anchor is omitted rather than altering the workbook. */ }
    const saved = sheetViews.get(ws.id);
    applyZoom(content, saved ? saved.zoom : fitZoom(content));
    viewport.scrollLeft = saved ? saved.x : 0;
    window.scrollTo(0, saved ? saved.y : 0);
}
function escapeNotice() {
    while(state.images.length)URL.revokeObjectURL(state.images.pop());
    notice.hidden=true;
}
async function loadExcel(bytes) {
    if (!window.ExcelJS) throw Error('Excel viewer bundle is not available.');
    // ExcelJS 4.4 assumes conventional drawing namespace prefixes and relative
    // image targets. Normalize equivalent valid OOXML in memory; the source
    // file is untouched and external relationships are never fetched.
    const zip=await window.JSZip.loadAsync(bytes);let normalized=false;
    for(const part of Object.values(zip.files)){
        if(/^xl\/drawings\/[^/]+\.xml$/.test(part.name)){
            const drawing=new DOMParser().parseFromString(await part.async('string'),'application/xml');let changed=false;
            for(const [ns,prefix] of [['http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing','xdr'],['http://schemas.openxmlformats.org/drawingml/2006/main','a']]){
                for(const node of Array.from(drawing.getElementsByTagNameNS(ns,'*'))){
                    if(node.prefix===prefix)continue;
                    const replacement=drawing.createElementNS(ns,prefix+':'+node.localName);
                    for(const attribute of Array.from(node.attributes)){
                        if(attribute.namespaceURI==='http://www.w3.org/2000/xmlns/'&&attribute.value===ns)continue;
                        replacement.setAttributeNS(attribute.namespaceURI,attribute.name,attribute.value);
                    }
                    while(node.firstChild)replacement.appendChild(node.firstChild);
                    node.parentNode.replaceChild(replacement,node);changed=true;
                }
            }
            if(changed){zip.file(part.name,new XMLSerializer().serializeToString(drawing));normalized=true;}
        }
        if(part.dir||!part.name.endsWith('.rels')||!part.name.includes('/_rels/'))continue;
        const xml=await part.async('string');
        if(!xml.includes('Target="/'))continue;
        const relations=new DOMParser().parseFromString(xml,'application/xml');let changed=false;
        for(const relation of Array.from(relations.getElementsByTagNameNS('*','Relationship'))){
            const target=relation.getAttribute('Target')||'';
            if(relation.getAttribute('TargetMode')==='External'||!target.startsWith('/xl/')||!zip.file(target.slice(1)))continue;
            const from=part.name.substring(0,part.name.lastIndexOf('/_rels/')).split('/');
            const to=target.slice(1).split('/');
            while(from.length&&to.length&&from[0]===to[0]){from.shift();to.shift();}
            relation.setAttribute('Target','../'.repeat(from.length)+to.join('/'));changed=true;
        }
        if(changed){zip.file(part.name,new XMLSerializer().serializeToString(relations));normalized=true;}
    }
    if(normalized)bytes=await zip.generateAsync({type:'uint8array'});
    const workbook=new ExcelJS.Workbook();
    await workbook.xlsx.load(bytes.buffer);
    state.workbook=workbook;
    if(!workbook.worksheets.length)throw Error('This spreadsheet has no worksheets.');
    sheetSelect.textContent='';
    workbook.worksheets.forEach(ws=>{
        const op=document.createElement('option');op.value=String(ws.id);op.textContent=ws.name;sheetSelect.appendChild(op);
    });
    sheetbar.hidden=false;
    sheetSelect.onchange=function() {
        escapeNotice();
        const sheet=workbook.getWorksheet(Number(sheetSelect.value));
        if(sheet)renderSheet(sheet);
    };
    sheetSelect.value=String(workbook.worksheets[0].id);
    renderSheet(workbook.worksheets[0]);
    info('Formula results use workbook cached values; formulas are not recalculated.');
}
async function loadWord(bytes) {
    if(!window.docx || !window.JSZip)throw Error('Word renderer bundle is not available.');
    content.innerHTML='';
    await window.docx.renderAsync(bytes,content,content,{
        breakPages:true,renderHeaders:true,renderFooters:true,
        renderFootnotes:true,renderEndnotes:true,renderAltChunks:false,
        ignoreLastRenderedPageBreak:false,debug:false
    });
    content.querySelectorAll('.docx-wrapper > section.docx').forEach(page => applyZoom(page, fitZoom(page)));
    info('Word preview preserves rich layout where supported; desktop pagination may differ.');
}
// Print media ignores reader zoom. Refuse incomplete/truncated worksheets.
window.prepareOfficePrint = function(landscape) {
    if (!window.officeReady) return 'Wait for the document to finish loading.';
    if (state.truncated) return 'This worksheet exceeds the preview limit. Choose a smaller worksheet before printing.';
    const table = content.querySelector('table.sheet');
    if (table) {
        const width = table.offsetWidth;
        const available = (landscape ? 277 : 190) * 96 / 25.4;
        const fit = Math.min(1, available / Math.max(1, width));
        if (fit < 0.35) return 'This sheet is too wide for readable A4 printing. Select a narrower worksheet or export a print area from Excel.';
        document.documentElement.style.setProperty('--sheet-print-scale', String(fit));
    }
    return null;
};
window.validateOfficeSnapshot = function() {
    if (!window.officeReady) throw Error('Wait for the document to finish loading.');
    if (state.truncated) throw Error('This worksheet exceeds the preview limit. Choose a smaller worksheet before exporting.');
};
(async function main(){
    try{
        const type=new URLSearchParams(location.search).get('type');
        if(type!=='word' && type!=='excel')throw Error('Unsupported format.');
        const res=await fetch('/document/current',{credentials:'omit',cache:'no-store'});
        if(!res.ok)throw Error('File access expired. Return and locate the document again.');
        const buf=await res.arrayBuffer();
        if(!buf.byteLength)throw Error('This document is empty.');
        if(buf.byteLength>35*1024*1024)throw Error('File exceeds the 35 MB preview safety limit.');
        const bytes=new Uint8Array(buf);
        message(type==='word'?'Rendering Word document…':'Reading Excel workbook…');
        if(type==='word')await loadWord(bytes); else await loadExcel(bytes);
        await document.fonts.ready;
        await Promise.all(Array.from(content.querySelectorAll("img")).map(img => img.decode().catch(() => {})));
        clearMessage();
        // Initial fit was applied independently to each page or worksheet.
        window.officeReady = true;
    }catch(error){
        message('Cannot display document: '+(error.message||String(error)));
        info('The original document was not modified. Try a different file or export to PDF.');
    }
})();
})();

// Capture rendered content into a bounded local snapshot for the independent PDF engine.
window.captureWReaderPdfSnapshot = function() {
    window.wReaderSnapshotReady=false;window.wReaderSnapshotError='';window.wReaderSnapshotHtml='';
    (async function(){try {
        window.validateOfficeSnapshot();
        const source=document.getElementById('content');const clone=source.cloneNode(true);
        const sourceSheet=source.querySelector('table.sheet'),copySheet=clone.querySelector('table.sheet');
        if(sourceSheet&&copySheet){
            // Column widths live in the on-screen heading, which print CSS hides.
            // Freeze the unzoomed geometry before hiding it or paginating merged rows.
            const widths=Array.from(sourceSheet.tHead.rows[0].cells).slice(1).map(c=>c.offsetWidth);
            const cols=document.createElement('colgroup');
            widths.forEach(width=>{const col=document.createElement('col');col.style.width=width+'px';cols.appendChild(col);});
            copySheet.querySelectorAll('colgroup').forEach(n=>n.remove());
            copySheet.insertBefore(cols,copySheet.firstChild);
            copySheet.style.width=widths.reduce((sum,w)=>sum+w,0)+'px';
            copySheet.style.tableLayout='fixed';
            copySheet.dataset.preserveColumns='true';
            const cells=sourceSheet.querySelectorAll('td'),copies=copySheet.querySelectorAll('td');
            cells.forEach((cell,i)=>copies[i].style.setProperty('white-space',getComputedStyle(cell).whiteSpace,'important'));
        }
        const originals=source.querySelectorAll('img');const copies=clone.querySelectorAll('img');
        for(let i=0;i<originals.length;i++){
            const img=originals[i];if(!img.complete)await img.decode();
            if(!img.naturalWidth)throw Error('A document image could not be exported.');
            if(img.src.startsWith('data:'))copies[i].src=img.src;
            else {
                const blob=await(await fetch(img.src)).blob();
                copies[i].src=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=reject;r.readAsDataURL(blob)});
            }
        }
        clone.querySelectorAll('script,iframe,object,embed').forEach(n=>n.remove());
        clone.style.zoom='1';clone.style.transform='none';
        clone.querySelectorAll('section.docx').forEach(page => { page.style.zoom='1';page.style.transform='none'; });
        const html=clone.outerHTML;if(html.length>24*1024*1024)throw Error('Document is too large to export.');
        window.wReaderSnapshotHtml=html;window.wReaderSnapshotReady=true;
    }catch(e){window.wReaderSnapshotError=String(e&&e.message||e)}})();
};
