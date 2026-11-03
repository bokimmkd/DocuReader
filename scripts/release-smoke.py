"""Black-box checks of an APK built from the actual release AAB. No app/test code linking."""
import subprocess, time, re, xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

ROOT=Path('verification/android-print/release-smoke'); ROOT.mkdir(parents=True,exist_ok=True)
def adb(*args):
 return subprocess.check_output(['adb',*args],stderr=subprocess.STDOUT)
def dump():
 adb('shell','uiautomator','dump','/sdcard/window.xml')
 raw=adb('shell','cat','/sdcard/window.xml').decode(errors='replace')
 return ET.fromstring(raw[raw.index('<?xml'):])
def match(value):
 tree=dump()
 return next((n for n in tree.iter('node') if value in (n.get('text'),n.get('content-desc'))),None)
def await_node(value,timeout=20,enabled=False):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  node=match(value)
  if node is not None and (not enabled or node.get("enabled")=="true"):return node
  time.sleep(.4)
 raise AssertionError('Release UI missing: '+value)
def await_text(fragment,timeout=20):
 end=time.monotonic()+timeout;attempts=0
 while time.monotonic()<end:
  if any(fragment in n.get('text','') for n in dump().iter('node')):return
  attempts+=1
  # Chromium can omit visible heading nodes after a local HTML/theme reload.
  # Verify actual pixels instead of treating that incomplete accessibility tree as blank content.
  if attempts>=2:
   shot=ROOT/('content-'+fragment+'.png');shot.write_bytes(adb('exec-out','screencap','-p'))
   visible=subprocess.check_output(['tesseract',str(shot),'stdout','--psm','11'],stderr=subprocess.DEVNULL).decode(errors='replace')
   (ROOT/('content-'+fragment+'.txt')).write_text(visible)
   clean=lambda value:re.sub('[^A-Z0-9]','',value.upper())
   if clean(fragment) in clean(visible):return
  time.sleep(.4)
 raise AssertionError('Release document content missing: '+fragment)
def menu_item(value):
 for _ in range(3):
  tree=dump()
  if any(value in (n.get('text'),n.get('content-desc')) for n in tree.iter('node')):return
  scrolls=[n for n in tree.iter('node') if n.get('scrollable')=='true']
  assert scrolls,'No scrollable category menu'
  a,b,c,d=[int(x) for x in re.findall(r'\d+',scrolls[-1].get('bounds',''))]
  adb('shell','input','swipe',str((a+c)//2),str(d-25),str((a+c)//2),str(b+25),'400')
 await_node(value)
def tap(value):
 node=await_node(value); bounds=[int(n) for n in re.findall(r'\d+',node.get('bounds',''))]
 assert len(bounds)==4,(value,bounds)
 adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
def capture(name,require_blue=False):
 out=ROOT/(name+'.png');out.write_bytes(adb('exec-out','screencap','-p'))
 if require_blue:
  im=Image.open(out).convert('RGB');n=sum(1 for y in range(0,im.height,4) for x in range(0,im.width,4) if (lambda c:c[2]>150 and c[2]>c[0]+60 and c[2]>c[1]+40)(im.getpixel((x,y))))
  assert n>500,(name,'blank frame',n)
def open_file(name):
 tap('Open document');await_node('Show roots');tap('Search')
 end=time.monotonic()+15
 while time.monotonic()<end:
  field=next((n for n in dump().iter('node') if n.get('resource-id')=='com.google.android.documentsui:id/search_src_text'),None)
  if field is not None:break
 else:raise AssertionError('System picker search field missing')
 adb('shell','input','keycombination','113','29') # Ctrl+A, including a restored search query.
 adb('shell','input','text',name);adb('shell','input','keyevent','KEYCODE_ENTER')
 end=time.monotonic()+30
 while time.monotonic()<end:
  # Do not mistake the query EditText for the actual file result.
  node=next((n for n in dump().iter('node') if n.get('resource-id')=='android:id/title' and n.get('text')==name),None)
  if node is not None:
   a,b,c,d=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
   if c>a and d>b:
    adb('shell','input','tap',str((a+c)//2),str((b+d)//2));return
  time.sleep(.4)
 raise AssertionError('Test fixture missing in system picker search: '+name)

def main():
 # The APK uses the production R8 graph and live configuration. Never click an ad.
 if b'package:com.blw.wreader' in adb('shell','pm','list','packages','com.blw.wreader'):adb('uninstall','com.blw.wreader')
 adb('install','-r','verification/release-universal.apk')
 component='com.blw.wreader/com.blw.documentreader.MainActivity'
 for i in range(3):
  adb('shell','am','force-stop','com.blw.wreader')
  adb('shell','am','start','-W','-n',component)
  await_node('Open document');time.sleep(.5);capture('startup-'+str(i),True)
  adb('shell','input','keyevent','KEYCODE_HOME');adb('shell','am','start','-W','-n',component)
  await_node('Open document');capture('resume-'+str(i),True)
 
 assets=Path('app/src/androidTest/assets')
 for source,name in [('sample.epub','release-book.epub'),('word.docx','release-word.docx'),('excel.xlsx','release-sheet.xlsx'),('picture.png','release-image.png')]:
  p=assets/source
  if p.exists():
   adb('push',str(p),'/sdcard/Download/'+name)
   adb('shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Download/'+name)
  else:raise AssertionError('Missing fixture '+source)
 
 open_file('release-book.epub');tap('Book mode');await_node('Aa');tap('Aa');tap('Sepia');tap('Done');await_text('EPUB_START');capture('book-sepia')
 adb('shell','input','keyevent','KEYCODE_BACK');await_node('Open document')
 for name in ['release-word.docx','release-sheet.xlsx','release-image.png']:
  open_file(name);await_node('Print',enabled=True)
  if name.endswith('.docx'):await_text('WORD_START')
  if name.endswith('.xlsx'):await_text('EXCEL_START')
  capture(name.split('.')[0],name.endswith('.png'));adb('shell','input','keyevent','KEYCODE_BACK');await_node('Open document')
 tap('Library');await_node('release-image.png');tap('Document actions');tap('Rename');tap('Uncategorized');menu_item('New category');capture('category-menu');adb('shell','input','keyevent','KEYCODE_BACK');tap('Cancel')
 tap('Document actions');tap('Remove from Library');tap('Remove');tap('Undo');await_node('release-image.png');capture('library-removal-undo')
 # Match the phone's ~384dp width and reserve 64dp for its banner. Real
 # Settings, branded header and bottom navigation must fit without scrolling.
 adb('shell','wm','size','1080x2160')
 adb('shell','wm','density','450')
 try:
  tap('Settings')
  await_node('Restore Purchase')
  footer=next((n for n in dump().iter('node') if n.get('text','').startswith('W Reader ') and 'English' in n.get('text','')),None)
  assert footer is not None, 'Settings version footer missing before scrolling'
  bounds=[int(v) for v in re.findall(r'\d+',footer.get('bounds',''))]
  nav=await_node('Home')
  nav_bounds=[int(v) for v in re.findall(r'\d+',nav.get('bounds',''))]
  assert 0 < bounds[1] < bounds[3] <= nav_bounds[1], 'Settings footer clipped by bottom navigation'
  capture('compact-settings')
 finally:
  adb('shell','wm','size','reset')
  adb('shell','wm','density','reset')
 print('PASS: actual release-AAB APK cold start/resume pixels, EPUB appearance, Word/Excel/image reader controls and Library removal/Undo.')

try:
 main()
except Exception:
 capture('failure')
 try:
  adb('shell','uiautomator','dump','/sdcard/window.xml')
  (ROOT/'failure.xml').write_bytes(adb('shell','cat','/sdcard/window.xml'))
  (ROOT/'failure-logcat.txt').write_bytes(adb('logcat','-d'))
 except Exception:pass
 raise
