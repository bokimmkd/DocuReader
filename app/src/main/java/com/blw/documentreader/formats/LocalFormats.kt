package com.blw.documentreader.formats

import com.blw.documentreader.data.TextContent
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Local, bounded document reading. Never executes scripts, resolves XML entities, or fetches resources. */
object LocalFormats {
    data class Page(val html:String,val notice:String="",val landscape:Boolean=false)
    fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    fun bounded(input:InputStream,limit:Int):ByteArray {
        val out=ByteArrayOutputStream();val buf=ByteArray(16384)
        while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit){"Document exceeds the safe reading size limit."};out.write(buf,0,n)}
        return out.toByteArray()
    }
    fun wrap(body:String)= """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'none'"><style>
        html,body{background:#fff;color:#172338;margin:0;font:16px/1.5 sans-serif}body{padding:18px;overflow-wrap:anywhere}img{max-width:100%;height:auto}table{border-collapse:collapse;max-width:100%;font-size:13px}td,th{border:1px solid #bccbdc;padding:5px;white-space:pre-wrap;overflow-wrap:anywhere}pre{white-space:pre-wrap}section.chapter{margin-bottom:32px}.slide{border:1px solid #bccbdc;padding:18px;margin-bottom:18px;background:white}.slide h2{color:#155cb2}.slide img{max-width:100%;height:auto;object-fit:contain}.image{text-align:center}.image img{max-width:100%;height:auto;object-fit:contain}
        @media print{body{padding:0;font-size:12pt}.slide{border:0;margin:0;break-after:page;padding:0}.slide:last-child{break-after:auto}.chapter{break-before:page}.chapter:first-child{break-before:auto}.image img{max-height:240mm}tr{break-inside:avoid}table{table-layout:fixed;width:100%;font-size:9pt}td,th{word-break:break-word}img{break-inside:avoid}}
        </style></head><body>"""+body+"</body></html>"
    fun read(type:String,input:InputStream):Page = when(type){
        "CSV"->Page(wrap(csv(TextContent.read(input))),landscape=true)
        "RTF"->Page(wrap("<div style='white-space:pre-wrap'>"+esc(rtf(bounded(input,8*1024*1024)))+"</div>"),"Text view · Embedded objects and advanced formatting are not displayed.")
        "EPUB"->epub(archive(input))
        "ODT"->odt(archive(input))
        "PowerPoint"->pptx(archive(input))
        else->error("This format is not available.")
    }
    fun csv(text:String):String {
        val first=text.lineSequence().firstOrNull().orEmpty()
        fun count(c:Char):Int {var quoted=false;var n=0;for(x in first){if(x=='"')quoted=!quoted else if(x==c&&!quoted)n++};return n}
        val delimiter=listOf(',', ';','\t').maxBy{count(it)}
        val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var i=0;var cells=0
        fun field(){require(++cells<=100000){"CSV has too many cells."};row.add(cell.toString());cell.clear()}
        fun line(){field();rows.add(row);row=mutableListOf();require(rows.size<=10000){"CSV has more than 10,000 rows."}}
        while(i<text.length){val c=text[i++];when {
            c=='"' && (quoted || cell.isEmpty())-> if(quoted && i<text.length && text[i]=='"'){cell.append('"');i++}else quoted=!quoted
            c==delimiter&&!quoted->field()
            (c=='\n'||c=='\r')&&!quoted->{if(c=='\r'&&i<text.length&&text[i]=='\n')i++;line()}
            else->cell.append(c)
        }}
        require(!quoted){"CSV contains an unfinished quoted field."}
        if(cell.isNotEmpty()||row.isNotEmpty())line()
        require(rows.all{it.size<=100}){"CSV is too wide (maximum 100 columns)."}
        return "<table>"+rows.mapIndexed{n,r->"<tr>"+r.joinToString(""){"<${if(n==0)"th" else "td"}>${esc(it)}</${if(n==0)"th" else "td"}>"}+"</tr>"}.joinToString("")+"</table>"
    }
    fun archive(input:InputStream):Map<String,ByteArray> {
        // Read compressed bytes through a cap as well as bounding decompressed entries.
        val bytes=bounded(input,24*1024*1024);val result=linkedMapOf<String,ByteArray>();var total=0;var entries=0
        ZipInputStream(bytes.inputStream()).use {z->while(true){val e=z.nextEntry?:break;require(++entries<=2500){"Document contains too many archive entries."};if(e.isDirectory)continue
            val name=e.name;require(!name.startsWith('/')&&!name.contains('\\')&&name.split('/').none{it==".."}){"Unsafe archive entry."}
            require(!result.containsKey(name)){"Duplicate archive entry."}
            val data=bounded(z,16*1024*1024);total+=data.size;require(total<=48*1024*1024){"Uncompressed document is too large."};result[name]=data
        }}
        require(result.isNotEmpty()){"Empty or unsupported document archive."};return result
    }
    private fun xml(bytes:ByteArray):Element {
        require(bytes.size<=4*1024*1024){"Document XML is too large."}
        require(bytes.none{it==0.toByte()}){"XML must use UTF-8 encoding."}
        val text=bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE",true)&&!text.contains("<!ENTITY",true)){"XML entity declarations are not supported."}
        val f=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;isExpandEntityReferences=false}
        runCatching{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}
        runCatching{f.setFeature("http://xml.org/sax/features/external-general-entities",false)}
        runCatching{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false)}
        return f.newDocumentBuilder().apply{setEntityResolver{_,_->throw IllegalArgumentException("External XML resources are blocked.")}}.parse(bytes.inputStream()).documentElement
    }
    private fun Element.all(local:String):List<Element> = getElementsByTagNameNS("*",local).let{n->(0 until n.length).map{n.item(it) as Element}}
    private fun Element.children():List<Element> = (0 until childNodes.length).map{childNodes.item(it)}.filterIsInstance<Element>()
    private fun Element.attr(local:String):String = (0 until attributes.length).map{attributes.item(it)}.firstOrNull{it.localName==local||it.nodeName==local}?.nodeValue.orEmpty()
    private fun resolve(base:String,href:String):String {
        require(!href.contains(':')&&!href.contains('\\')){"External document resources are not supported."}
        val parts=mutableListOf<String>();for(p in (if(href.startsWith("/")) href.substringBefore('#') else base.substringBeforeLast('/',"")+"/"+href.substringBefore('#')).split('/')){when(p){"","."->{};".."->{require(parts.isNotEmpty()){"Invalid document path."};parts.removeAt(parts.lastIndex)};else->parts.add(java.net.URLDecoder.decode(p.replace("+","%2B"),"UTF-8"))}}
        return parts.joinToString("/")
    }
    private fun image(data:ByteArray?,path:String):String {
        if(data==null)return "<p>[Image unavailable]</p>"
        val mime=when(path.substringAfterLast('.').lowercase()){ "jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";"gif"->"image/gif";else->return "<p>[Unsupported image]</p>"}
        require(data.size<=4*1024*1024){"An embedded image is too large."}
        return "<img alt='Document image' src='data:$mime;base64,${Base64.getEncoder().encodeToString(data)}'>"
    }
    private fun checkPage(body:String):String {require(body.length<=20*1024*1024){"Rendered document is too large."};return body}
    private fun epub(files:Map<String,ByteArray>):Page {
        require(files["META-INF/encryption.xml"]==null){"Encrypted or DRM-protected EPUB books are not supported."}
        val container=xml(files["META-INF/container.xml"]?:error("EPUB container is missing."))
        val opf=container.all("rootfile").firstOrNull()?.attr("full-path")?:error("EPUB package is missing.")
        val book=xml(files[opf]?:error("EPUB package is missing."));val items=book.all("item").associateBy{it.attr("id")}
        val chapters=book.all("itemref").filter{it.attr("linear")!="no"};require(chapters.isNotEmpty()&&chapters.size<=300){"EPUB has no readable chapters or too many chapters."}
        val safe=Safelist.relaxed().removeTags("a","img").addTags("section","figure","figcaption")
        val body=buildString{for(ref in chapters){val item=items[ref.attr("idref")]?:error("EPUB chapter reference is missing.");val path=resolve(opf,item.attr("href"));val data=files[path]?:error("EPUB chapter is missing.")
            val doc=Jsoup.parse(data.toString(Charsets.UTF_8));doc.select("script,style,iframe,object,embed,svg,form,link,meta").remove()
            val token="WREADER"+java.util.UUID.randomUUID().toString().replace("-","");val images=mutableListOf<String>();doc.select("img").forEach{img->val source=img.attr("src");val html=runCatching{image(files[resolve(path,source)],source)}.getOrDefault("<p>[External image omitted]</p>");require(images.sumOf{it.length}.toLong()+html.length<=20*1024*1024){"EPUB images are too large."};val marker="${token}IMAGE${images.size}END";images.add(html);img.replaceWith(org.jsoup.nodes.TextNode(marker))}
            var clean=Jsoup.clean(doc.body().html(),safe);images.forEachIndexed{i,img->clean=clean.replace("${token}IMAGE${i}END",img)}
            append("<section class='chapter'>");append(clean);append("</section>");require(length<=20*1024*1024){"EPUB is too large to render."}
        }}
        return Page(wrap(checkPage(body)),"Reflowed book · Original fonts, scripts and interactive content are not used.")
    }
    private fun odt(files:Map<String,ByteArray>):Page {
        require(files["META-INF/manifest.xml"]?.toString(Charsets.UTF_8)?.contains("encryption-data")!=true){"Encrypted ODT files are not supported."}
        val root=xml(files["content.xml"]?:error("ODT content is missing."))
        fun render(n:Node):String {
            if(n.nodeType==Node.TEXT_NODE)return esc(n.nodeValue.orEmpty())
            if(n !is Element)return ""
            val name=n.localName;val content={buildString{val l=n.childNodes;for(i in 0 until l.length){append(render(l.item(i)));require(length<=20*1024*1024){"ODT content is too large."}}}}
            return when(name){
                "p"->"<p>${content()}</p>";"h"->"<h2>${content()}</h2>";"list"->"<ul>${content()}</ul>";"list-item"->"<li>${content()}</li>"
                "table"->"<table>${content()}</table>";"table-row"->{val count=n.attr("number-rows-repeated").toIntOrNull()?:1;require(count in 1..1000){"ODT repeated rows are too large."};("<tr>${content()}</tr>").let{require(it.length.toLong()*count<=20*1024*1024){"ODT repeated rows are too large."};it.repeat(count)}}
                "table-cell"->{val count=n.attr("number-columns-repeated").toIntOrNull()?:1;require(count in 1..100){"ODT repeated columns are too large."};("<td>${content()}</td>").let{require(it.length.toLong()*count<=20*1024*1024){"ODT repeated columns are too large."};it.repeat(count)}}
                "line-break"->"<br>";"tab"->"&#9;";"s"->" ".repeat((n.attr("c").toIntOrNull()?:1).coerceIn(1,100))
                "image"->{val path=n.attr("href").removePrefix("./");image(files[path],path)}
                "annotation","tracked-changes","scripts","binary-data"->""
                else->content()
            }.also{require(it.length<=20*1024*1024){"ODT content is too large."}}
        }
        val text=root.all("text").firstOrNull()?:error("ODT has no document text.")
        return Page(wrap(checkPage(render(text))),"Reflowed document · Text, tables and embedded images. Advanced page layout may differ.")
    }
    private fun pptx(files:Map<String,ByteArray>):Page {
        val root=xml(files["ppt/presentation.xml"]?:error("PPTX presentation is missing."))
        val rels=xml(files["ppt/_rels/presentation.xml.rels"]?:error("PPTX slide references are missing.")).all("Relationship").associateBy{it.attr("Id")}
        val ids=root.all("sldId");require(ids.isNotEmpty()&&ids.size<=300){"Presentation is empty or exceeds 300 slides."}
        val body=buildString{ids.forEachIndexed{i,id->val rid=id.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id")
            val rel=rels[rid]?:error("Slide reference is missing.");require(rel.attr("TargetMode")!="External"){"External slides are not supported."}
            val path=resolve("ppt/presentation.xml",rel.attr("Target"));val slide=xml(files[path]?:error("Slide is missing."))
            val relPath=path.substringBeforeLast('/')+"/_rels/"+path.substringAfterLast('/')+".rels"
            val sr=files[relPath]?.let{xml(it).all("Relationship").associateBy{it.attr("Id")}}.orEmpty()
            append("<section class='slide'><h2>Slide ${i+1}</h2>")
            val shapes=slide.all("spTree").firstOrNull()?.children().orEmpty()
            fun shape(n:Element):String=when(n.localName){
                "sp"->n.all("p").joinToString(""){p->"<p>"+p.all("t").joinToString(""){esc(it.textContent)}+"</p>"}
                "pic"->{val blip=n.all("blip").firstOrNull();val link=blip?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","embed");val r=sr[link];if(r==null||r.attr("TargetMode")=="External")"<p>[External image omitted]</p>" else {val target=resolve(path,r.attr("Target"));image(files[target],target)}}
                "grpSp"->n.children().joinToString(""){shape(it)}
                "graphicFrame"->if(n.all("tbl").isNotEmpty()) "<table>"+n.all("tr").joinToString(""){row->"<tr>"+row.children().filter{it.localName=="tc"}.joinToString(""){cell->"<td>"+cell.all("t").joinToString(" "){esc(it.textContent)}+"</td>"}+"</tr>"}+"</table>" else "<p>[Chart, diagram or embedded object preview unavailable]</p>"
                else->""
            }
            shapes.forEach{append(shape(it))};append("</section>");require(length<=20*1024*1024){"Presentation is too large to render."}
        }}
        return Page(wrap(body),"Slide reading view · Text, tables and images. Themes, exact positioning, animations and charts are not reproduced.",true)
    }
    /** RTF text decoding: balanced destinations, Unicode fallbacks, ANSI codepages, escaped braces. */
    fun rtf(bytes:ByteArray):String {
        val s=bytes.toString(Charsets.ISO_8859_1);require(s.startsWith("{\\rtf")){"Not a valid RTF document."}
        data class State(var skip:Boolean=false,var uc:Int=1)
        var state=State();val stack=java.util.ArrayDeque<State>();val out=StringBuilder();var i=0;var skipChars=0;var codepage="windows-1252"
        val ignored=setOf("fonttbl","colortbl","stylesheet","info","pict","object","header","footer","filetbl","listtable","listoverridetable","generator","datastore","themedata","xmlnstbl")
        fun emit(c:Char){if(skipChars>0)skipChars-- else if(!state.skip)out.append(c)}
        while(i<s.length){val c=s[i++];when(c){
            '{'->{require(stack.size<1000){"RTF nesting is too deep."};stack.push(state.copy())}
            '}'->{require(stack.isNotEmpty()){"Unbalanced RTF document."};state=stack.pop()}
            '\\'->{if(i>=s.length)break;val x=s[i++]
                when {
                    x=='\\'||x=='{'||x=='}'->emit(x)
                    x=='*'->state.skip=true
                    x=='\''->{require(i+2<=s.length){"Incomplete RTF escape."};val b=s.substring(i,i+2).toIntOrNull(16)?:error("Invalid RTF escape.");i+=2;emit(byteArrayOf(b.toByte()).toString(charset(codepage))[0])}
                    x.isLetter()->{val start=i-1;while(i<s.length&&s[i].isLetter())i++;val word=s.substring(start,i);val nstart=i;if(i<s.length&&s[i]=='-')i++;while(i<s.length&&s[i].isDigit())i++;val n=s.substring(nstart,i).toIntOrNull();if(i<s.length&&s[i]==' ')i++
                        if(word in ignored)state.skip=true
                        when(word){"ansicpg"->codepage=if(n==1251)"windows-1251" else "windows-1252";"uc"->state.uc=(n?:1).coerceIn(0,10)
                            "u"->{if(!state.skip)out.append(((n?:0) and 65535).toChar());skipChars=state.uc}
                            "par","line"->if(!state.skip)out.append('\n');"tab"->emit('\t');"emdash"->emit('—');"endash"->emit('–');"bullet"->emit('•')
                            "bin"->{require(n!=null&&n>=0&&i+n<=s.length){"Invalid RTF binary block."};i+=n}
                        }
                    }
                    x=='~'->emit(' ')
                }
            }
            '\r','\n'->{}
            else->emit(if(c.code>=128)byteArrayOf(c.code.toByte()).toString(charset(codepage))[0] else c)
        }}
        require(stack.isEmpty()){"Unbalanced RTF document."};return out.toString()
    }
}
