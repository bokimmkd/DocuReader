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
def await_node(value,timeout=20):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  node=match(value)
  if node is not None:return node
  time.sleep(.4)
 raise AssertionError('Release UI missing: '+value)
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
 tap('Open document')
 # The system picker supplies the real content URI grant, just as on the phone.
 tree=dump()
 root_button=next((n.get('content-desc') for n in tree.iter('node') if n.get('content-desc') in ('Show roots','Open navigation drawer')),None)
 if root_button:
  tap(root_button);tap('Downloads')
 tap(name)

def main():
 # The APK uses the production R8 graph and live configuration. Never click an ad.
 adb('uninstall','com.blw.wreader') # Independent fresh install; QA and smoke signing keys differ.
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
  if p.exists():adb('push',str(p),'/sdcard/Download/'+name)
  else:raise AssertionError('Missing fixture '+source)
 
 open_file('release-book.epub');await_node('Aa');tap('Aa');tap('Sepia');tap('Done');capture('book-sepia')
 adb('shell','input','keyevent','KEYCODE_BACK');await_node('Open document')
 for name in ['release-word.docx','release-sheet.xlsx','release-image.png']:
  open_file(name);await_node('Print');capture(name.split('.')[0]);adb('shell','input','keyevent','KEYCODE_BACK');await_node('Open document')
 tap('Library');await_node('release-image.png');tap('Document actions');tap('Rename');tap('Uncategorized');await_node('New category');capture('category-menu');adb('shell','input','keyevent','KEYCODE_BACK');tap('Cancel')
 tap('Document actions');tap('Remove from Library');tap('Remove');tap('Undo');await_node('release-image.png');capture('library-removal-undo')
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
