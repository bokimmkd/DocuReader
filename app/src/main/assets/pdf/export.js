/* Trusted offline PDF renderer. No document scripts, remote services or print UI. */
(async function(){
 'use strict';
 window.wReaderPdf={state:'loading'};
 try {
  const data=await(await fetch('/export/current')).json();
  const holder=document.getElementById('document');
  const parsed=new DOMParser().parseFromString(data.html,'text/html');
  parsed.querySelectorAll('script,iframe,object,embed,link,base,meta,form,input,button').forEach(n=>n.remove());
  parsed.querySelectorAll('*').forEach(n=>Array.from(n.attributes).forEach(a=>{
   if(/^on/i.test(a.name)||a.name==='href'||a.name==='srcdoc')n.removeAttribute(a.name);
  }));
  const style=document.createElement('style');style.textContent=data.css.replace(/@media\s+print/g,'@media all');document.head.appendChild(style);
  const override=document.createElement('style');override.textContent=`
   html,body{background:#fff!important;color:#172338!important;margin:0!important;padding:0!important}
   #document{width:${data.width}px;margin:0;padding:0;overflow:visible}
   #document,#document *,.wreader-pdf-page,.wreader-pdf-page *{font-family:WReaderSans!important}
   #content{zoom:1!important;transform:none!important;min-height:0!important}
   #viewport{overflow:visible!important;width:auto!important}
   .docx-wrapper{padding:0!important;background:#fff!important}
   .docx-wrapper>section.docx{box-shadow:none!important;margin:0!important;min-height:0!important;height:auto!important;overflow:visible!important;max-width:100%!important}
   .image img,.slide img{max-height:none!important;height:auto;max-width:100%}
   #sheetbar,#status,#notice{display:none!important}
  `;document.head.appendChild(override);
  holder.appendChild(document.importNode(parsed.body,true));
  await Promise.all(Array.from(holder.querySelectorAll('img')).map(img=>img.decode()));
  const doc=new window.jspdf.jsPDF({orientation:data.landscape?'landscape':'portrait',unit:'mm',format:'a4',compress:true,putOnlyUsedFonts:true});
  const fonts=[['NotoSans-Regular','WReaderSans','normal'],['NotoSans-Bold','WReaderSans','bold'],['NotoSans-Italic','WReaderSans','italic'],['NotoSans-BoldItalic','WReaderSans','bolditalic'],['NotoSansMath-Regular','WReaderMath','normal'],['NotoSansSymbols-Regular','WReaderSymbols','normal'],['NotoSansSymbols2-Regular','WReaderSymbols2','normal']];
  for(const [file,family,style]of fonts){
   const bytes=new Uint8Array(await(await fetch('fonts/'+file+'.ttf')).arrayBuffer());
   let binary='';for(let i=0;i<bytes.length;i+=16384)binary+=String.fromCharCode.apply(null,bytes.subarray(i,i+16384));
   doc.addFileToVFS(file+'.ttf',binary);doc.addFont(file+'.ttf',family,style);
  }
  const families=['WReaderSans','WReaderMath','WReaderSymbols','WReaderSymbols2'];
  const metadata=families.map(f=>doc.getFont(f,'normal').metadata);
  const walker=document.createTreeWalker(holder,NodeFilter.SHOW_TEXT);const nodes=[];
  while(walker.nextNode()){if(!walker.currentNode.parentElement.closest('style,script'))nodes.push(walker.currentNode);if(nodes.length>50000)throw Error('This document has too much text to export.');}
  for(const node of nodes){
   const groups=[];let family=0,run='';
   for(const ch of node.nodeValue){
    const cp=ch.codePointAt(0);let selected=0;
    if(cp>=32&&!(cp>=0x200b&&cp<=0x200f)&&!(cp>=0xfe00&&cp<=0xfe0f)){
     selected=cp>65535?-1:metadata.findIndex(m=>m.characterToGlyph(cp)>0);
     if(selected<0)throw Error('Some characters need a different PDF font. Use Print and choose Save as PDF for this document.');
    }
    if(selected!==family&&run){groups.push([family,run]);run='';}family=selected;run+=ch;
   }
   if(run)groups.push([family,run]);
   if(groups.some(g=>g[0]!==0)){
    const fragment=document.createDocumentFragment();for(const [f,text]of groups){const span=document.createElement('span');span.style.setProperty('font-family',families[f],'important');if(f){span.style.fontWeight='normal';span.style.fontStyle='normal';}span.textContent=text;fragment.appendChild(span);}node.replaceWith(fragment);
   }
  }
  await document.fonts.ready;
  doc.setFont('WReaderSans','normal');
  const options={x:0,y:0,width:doc.internal.pageSize.getWidth()-20,windowWidth:data.width,
   margin:[10,10,10,10],autoPaging:'text',fontFaces:[],
   html2canvas:{backgroundColor:'#ffffff',logging:false,useCORS:false,allowTaint:false,windowWidth:data.width}};
  const tables=holder.querySelectorAll('table');
  const sheet=holder.querySelector('table.sheet')||(tables.length===1&&holder.textContent.trim()===tables[0].textContent.trim()?tables[0]:null);
  if(sheet){
   // Paginate complete row groups before painting. Generic canvas auto-paging can lose a row at a boundary.
   const rowList=Array.from(sheet.tBodies).flatMap(b=>Array.from(b.rows));
   const width=sheet.offsetWidth;
   if(width>data.width+1)throw Error('This table is too wide for direct PDF. Choose Landscape or use Print and choose Save as PDF.');
   const groups=[];
   for(let start=0;start<rowList.length;){
    let end=start+1;
    for(let i=start;i<end;i++)for(const cell of rowList[i].cells)end=Math.min(rowList.length,Math.max(end,i+cell.rowSpan));
    groups.push(rowList.slice(start,end));start=end;
   }
   const template=sheet.cloneNode(false);template.style.width=width+'px';
   const columns=Array.from(sheet.rows).find(r=>r.getBoundingClientRect().height>0&&Array.from(r.cells).every(c=>c.colSpan===1));
   if(columns){const cg=document.createElement('colgroup');for(const cell of columns.cells){const col=document.createElement('col');col.style.width=cell.offsetWidth+'px';cg.appendChild(col);}template.appendChild(cg);}
   const pagePixels=(doc.internal.pageSize.getHeight()-20)*data.width/(doc.internal.pageSize.getWidth()-20);
   const maximum=pagePixels-16;
   holder.replaceChildren();let box=null,table=null,body=null;const pages=[];
   function page(){box=document.createElement('div');box.className='wreader-pdf-page';box.style.width=data.width+'px';table=template.cloneNode(true);body=document.createElement('tbody');table.appendChild(body);box.appendChild(table);holder.appendChild(box);pages.push(box);}
   page();
   for(const group of groups){
    const nodes=group.map(r=>r.cloneNode(true));nodes.forEach(n=>body.appendChild(n));
    if(box.getBoundingClientRect().height>maximum){
     nodes.forEach(n=>n.remove());if(!body.rows.length)throw Error('A table row is too tall for one PDF page. Use Print and choose Save as PDF.');
     page();nodes.forEach(n=>body.appendChild(n));
     if(box.getBoundingClientRect().height>maximum)throw Error('A merged table row is too tall for one PDF page. Use Print and choose Save as PDF.');
    }
   }
   // One continuous paint preserves jsPDF's page index, fonts and margin transforms.
   // Empty space after each complete group aligns the next table with the next printable page.
   pages.forEach((p,n)=>{if(n<pages.length-1)p.style.height=pagePixels+'px';});
   await doc.html(holder,options);
  }else await doc.html(holder,options);
  const bytes=new Uint8Array(doc.output('arraybuffer'));
  if(bytes.length>64*1024*1024)throw Error('The PDF exceeds the 64 MB export limit.');
  window.wReaderPdf={state:'done',bytes:bytes,size:bytes.length,pages:doc.getNumberOfPages()};
 }catch(e){window.wReaderPdf={state:'error',error:String(e&&e.message||e)}}
})();
window.wReaderPdfChunk=function(offset,length){
 const b=window.wReaderPdf.bytes;if(!b)throw Error('PDF is not ready');
 return btoa(String.fromCharCode.apply(null,b.subarray(offset,offset+length)));
};
