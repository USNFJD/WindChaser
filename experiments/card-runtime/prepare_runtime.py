"""Reproducible lab-only modifications to pinned LGPL-3.0-or-later DroidPlugin.
No guest APK is patched. Run after fetching the documented pinned archive.
The added CardWindowAdapter.java is supplied in the corresponding-source tree
and in upstream-changes.patch; this script records the edits to existing files.
"""
from pathlib import Path
import re

root=Path(__file__).resolve().parent
lib=root/'upstream/project/Libraries/DroidPlugin'
(lib/'build.gradle').write_text('''apply plugin: 'com.android.library'
android {
    namespace 'com.morgoo.droidplugin'
    compileSdk 35
    defaultConfig {
        minSdk 19
        buildConfigField 'String', 'AUTHORITY_NAME', '"cn.hydrogen.cardlab.droidplugin_stub"'
        manifestPlaceholders = [authorityName:'cn.hydrogen.cardlab.droidplugin_stub']
    }
    buildFeatures { aidl true; buildConfig true }
    compileOptions { sourceCompatibility JavaVersion.VERSION_1_8; targetCompatibility JavaVersion.VERSION_1_8 }
    lint { abortOnError false }
}
dependencies { compileOnly fileTree(dir:'lib',include:['*.jar']) }
''',encoding='utf-8')
manifest=lib/'src/main/AndroidManifest.xml'
text=manifest.read_text(encoding='utf-8')
text=text.replace('    package="com.morgoo.droidplugin"','')
# Avoid the upstream sample's blanket permission list: host requests lab needs only.
text=re.sub(r'<uses-permission\b[^>]*/>', '', text)
text=re.sub(r'<uses-feature\b[^>]*/>', '', text)
text=text.replace('android:noHistory="true"','android:noHistory="false"')
def affinity(match):
    block=match.group(0)
    process=re.search(r'ActivityStub\$(?:Dialog\$)?(P\d\d)\$',block)
    if process and 'android:taskAffinity=' not in block:
        block=block.replace('<activity','<activity android:taskAffinity="cn.hydrogen.cardlab.task.'+process.group(1)+'"',1)
    return block
text=re.sub(r'<activity\b.*?</activity>',affinity,text,flags=re.S)
manifest.write_text(text,encoding='utf-8')
for styles in (lib/'src/main/res').glob('values*/styles.xml'):
    text=styles.read_text(encoding='utf-8')
    text=text.replace('<item name="android:windowBackground">@drawable/plugin_activity_loading</item>', '''<item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:backgroundDimEnabled">false</item>''')
    styles.write_text(text,encoding='utf-8')
instrumentation=lib/'src/main/java/com/morgoo/droidplugin/hook/handle/PluginInstrumentation.java'
text=instrumentation.read_text(encoding='utf-8')
needle='            mTarget.callActivityOnCreate(activity, icicle);'
if 'CardWindowAdapter.register' not in text:
    text=text.replace('        if (mTarget != null) {\n'+needle, '        com.morgoo.droidplugin.card.CardWindowAdapter.register(mHostContext, activity);\n        if (mTarget != null) {\n'+needle,1)
instrumentation.write_text(text,encoding='utf-8')
session=lib/'src/main/java/com/morgoo/droidplugin/hook/handle/IWindowSessionInvokeHandle.java'
text=session.read_text(encoding='utf-8')
needle='                    WindowManager.LayoutParams attr = ((WindowManager.LayoutParams) args[index]);'
if 'CardWindowAdapter.apply' not in text:
    text=text.replace(needle,needle+'\n                    com.morgoo.droidplugin.card.CardWindowAdapter.apply(mHostContext, attr);')
session.write_text(text,encoding='utf-8')
# Upstream incorrectly prefixes already-qualified component names (startsWith("")).
callback=lib/'src/main/java/com/morgoo/droidplugin/hook/handle/PluginCallback.java'
text=callback.read_text(encoding='utf-8').replace('startsWith("")','startsWith(".")')
callback.write_text(text,encoding='utf-8')
print('Prepared pinned runtime: Gradle compatibility, minimal permissions, translucent stubs, window hook.')
