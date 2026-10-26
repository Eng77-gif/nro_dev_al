@echo off
REM Ghep file resources.assets.resS (bi cat do gioi han 100MB cua GitHub)
cd /d "%~dp0"
for /d %%D in (*MOD) do (
  copy /b "%%D\XUNGLORDLOCAL_Data\resources.assets.resS.part00" + "%%D\XUNGLORDLOCAL_Data\resources.assets.resS.part01" "%%D\XUNGLORDLOCAL_Data\resources.assets.resS"
)
echo Da ghep xong.
pause
