# Third-Party Notices

This product includes or links to third-party software subject to the following
licenses. Source URLs and copyright notices are reproduced below as required by
each license.

## libmpv-android (MIT)

Consumed as the Maven coordinate `dev.jdtech.mpv:libmpv:1.0.0`.

- Source: https://github.com/jarnedemeulemeester/libmpv-android
- License: MIT
- Copyright (c) 2023 Jarne Demeulemeester

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## mpv-android (MIT)

`libmpv-android` is based on `mpv-android`, whose Kotlin/Java and JNI shim
patterns informed the design of our player surface.

- Source: https://github.com/mpv-android/mpv-android
- License: MIT
- Copyright (c) 2016 Ilya Zhuravlev
- Copyright (c) 2016 sfan5 <sfan5@live.de>

The MIT text above applies.

## libmpv (GPL-2.0-or-later / LGPL-2.1-or-later)

The `libmpv.so` bundled inside `dev.jdtech.mpv:libmpv:1.0.0` is built from the
mpv media player's `libmpv` client API.

- Source: https://github.com/mpv-player/mpv
- License: GPL-2.0-or-later (mpv) / LGPL-2.1-or-later (libmpv)
- Copyright (c) the mpv developers

The full text of the GNU GPL and LGPL is available at:
- https://www.gnu.org/licenses/gpl-2.0.html
- https://www.gnu.org/licenses/lgpl-2.1.html

## ffmpeg (LGPL-2.1-or-later, or GPL-2.0-or-later depending on build flags)

The `libav*.so`, `libsw*.so` libraries bundled inside the AAR are part of
FFmpeg.

- Source: https://ffmpeg.org
- License: LGPL-2.1-or-later (default) / GPL-2.0-or-later (if configured)
- Copyright (c) the FFmpeg developers

## Bundled dependency libraries

The `libmpv-android` AAR also bundles the following libraries, each under its
respective license (versions per the AAR's v1.0.0 release notes):

| Library      | Version  | License         | Source |
|--------------|----------|-----------------|--------|
| lua          | 5.2.4    | MIT             | https://www.lua.org |
| libunibreak  | 6.1      | zlib            | https://github.com/nicowilliams/libunibreak |
| libass       | 0.17.4   | ISC             | https://github.com/libass/libass |
| harfbuzz     | 14.1.0   | MIT             | https://harfbuzz.github.io |
| fribidi      | 1.0.16   | LGPL-2.1-or-later | https://github.com/fribidi/fribidi |
| freetype     | 2.14.3   | FTL / GPL-2.0-or-later | https://freetype.org |
| libxml2      | 2.15.2   | MIT             | https://gitlab.gnome.org/GNOME/libxml2 |
| fontconfig   | 2.17.1   | MIT             | https://www.freedesktop.org/wiki/Software/fontconfig/ |
| mbedtls      | 3.6.6    | Apache-2.0      | https://www.trustedfirmware.org/projects/mbed-tls/ |
| libplacebo    | 7.360.1  | LGPL-2.1-or-later | https://code.videolan.org/videolan/libplacebo |
| dav1d        | 1.5.3    | BSD-2-Clause    | https://code.videolan.org/videolan/dav1d |

## ezmpv

This project (`ezmpv`) is licensed under GPL-3.0 — see [LICENSE](LICENSE).
Where required, the above notices are retained in accordance with their
respective licenses.