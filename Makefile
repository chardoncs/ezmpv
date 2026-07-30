.PHONY: release-arm64 release-x86_64 release-all release-universal \
        install-arm64 install-x86_64 install-universal \
        debug debug-install lint clean-signing \
        verify-arm64 verify-x86_64 verify-universal

GRADLE       ?= ./gradlew
BUILD_TOOLS   = $(firstword $(wildcard $(ANDROID_HOME)/build-tools/*/apksigner) $(firstword $(wildcard $(ANDROID_SDK_ROOT)/build-tools/*/apksigner)))
APK_DIR      = app/build/outputs/apk/release
APK_NAME     = app-release.apk
VERSION      = $(shell awk -F'"' '/versionName =/ {print $$2}' app/build.gradle.kts)
APK_ARM64    = ezmpv-$(VERSION)-arm64-v8a.apk
APK_X86_64   = ezmpv-$(VERSION)-x86_64.apk
APK_UNIVERSAL = ezmpv-$(VERSION)-universal.apk
# Copy signed APKs here so a subsequent ABI build's packageRelease task
# (which cleans APK_DIR) doesn't clobber prior outputs.
OUT_DIR      = release

# Build one ABI's signed release APK (overwrites app-release.apk each time).
# After the build, copy/rename it to OUT_DIR so a subsequent ABI build doesn't clobber it.
define build_abi
	@if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
	echo "===> Building $1 release APK (versionCode $2)"; \
	$(GRADLE) :app:assembleRelease -PtargetAbi=$1 -PabiVercodeSuffix=$2; \
	mkdir -p $(OUT_DIR); \
	cp -f $(APK_DIR)/$(APK_NAME) $(OUT_DIR)/$(APK_OUT); \
	echo "===> $(OUT_DIR)/$(APK_OUT) ready"
endef

release-arm64: APK_OUT := $(APK_ARM64)
release-arm64:
	$(call build_abi,arm64-v8a,1)

release-x86_64: APK_OUT := $(APK_X86_64)
release-x86_64:
	$(call build_abi,x86_64,4)

# Build both ABI APKs in one invocation (arm64 first, then x86_64).
release-all: release-arm64 release-x86_64
	@echo "===> All ABI release APKs:"; \
	ls -l $(OUT_DIR)/$(APK_ARM64) $(OUT_DIR)/$(APK_X86_64)

# Universal APK (both ABIs, versionCode 1). Renamed to ezmpv-<version>-universal.apk.
release-universal:
	@if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
	echo "===> Building universal release APK (versionCode 1)"; \
	$(GRADLE) :app:assembleRelease
	mkdir -p $(OUT_DIR); \
	cp -f $(APK_DIR)/$(APK_NAME) $(OUT_DIR)/$(APK_UNIVERSAL)
	@echo "===> $(OUT_DIR)/$(APK_UNIVERSAL) ready"

# Debug build + install helpers.
debug:
	$(GRADLE) :app:assembleDebug

debug-install:
	$(GRADLE) :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

install-arm64: release-arm64
	adb install -r $(OUT_DIR)/$(APK_ARM64)

install-x86_64: release-x86_64
	adb install -r $(OUT_DIR)/$(APK_X86_64)

install-universal: release-universal
	adb install -r $(OUT_DIR)/$(APK_UNIVERSAL)

# Verify APK signatures with apksigner (auto-detected from the SDK).
define verify_apk
	@if [ -z "$(BUILD_TOOLS)" ]; then \
		echo "apksigner not found (set ANDROID_HOME or ANDROID_SDK_ROOT)"; exit 1; fi; \
	echo "===> Verifying $1"; \
	$(BUILD_TOOLS) verify --verbose --print-certs $1
endef

verify-arm64: release-arm64
	$(call verify_apk,$(OUT_DIR)/$(APK_ARM64))

verify-x86_64: release-x86_64
	$(call verify_apk,$(OUT_DIR)/$(APK_X86_64))

verify-universal: release-universal
	$(call verify_apk,$(OUT_DIR)/$(APK_UNIVERSAL))

# Convenience targets.
lint:
	$(GRADLE) :app:lint

# Remove built release APKs (staging dir + gradle output dir).
clean-signing:
	rm -f $(OUT_DIR)/$(APK_ARM64) $(OUT_DIR)/$(APK_X86_64) $(OUT_DIR)/$(APK_UNIVERSAL) $(APK_DIR)/$(APK_NAME)