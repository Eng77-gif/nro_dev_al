#!/bin/sh
# Ghép file resources.assets.resS (bị cắt do giới hạn 100MB của GitHub)
cd "$(dirname "$0")/LÂU CỒ MOD/XUNGLORDLOCAL_Data" && cat resources.assets.resS.part00 resources.assets.resS.part01 > resources.assets.resS && sha256sum -c resources.assets.resS.sha256 2>/dev/null || true
