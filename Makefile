.PHONY: release-arm64 release-x86_64 release-all release-universal \
        install-arm64 install-x86_64 install-universal \
        debug debug-install lint clean-signing \
        verify-arm64 verify-x86_64 verify-universal

GRADLE       ?= ./gradlew
BUILD_TOOLS   = $(firstword $(wildcard $(ANDROID_HOME)/build-tools/*/apksigner) $(firstword $(wildcard $(ANDROID_SDK_ROOT)/build-tools/*/apksigner)))
APK_DIR      = app/build/outputs/apk/release
APK_NAME     = app-release.apk
APK_ARM64    = app-arm64-v8a-release.apk
APK_X86_64   = app-x86_64-release.apk

# Source .env if present (exports EZMPV_KEYSTORE* into the environment).
ENV_LOADED := $(if $(wildcard .env),$(eval -include $(shell set -a; . ./.env; set +a >/dev/null 2>&1)),)

# Build one ABI's signed release APK (overwrites app-release.apk each time).
# After the build, copy/rename it so a subsequent ABI build doesn't clobber it.
define build_abi
	@if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
	echo "===> Building $1 release APK (versionCode $2)"; \
	$(GRADLE) :app:assembleRelease -PtargetAbi=$1 -PabiVercodeSuffix=$2; \
	cp -f $(APK_DIR)/$(APK_NAME) $(APK_DIR)/$(APK_OUT); \
	echo "===> $(APK_DIR)/$(APK_OUT) ready"
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
	ls -l $(APK_DIR)/$(APK_ARM64) $(APK_DIR)/$(APK_X86_64)

# Universal APK (both ABIs, versionCode 1). Leaves app-release.apk as-is.
release-universal:
	@if [ -f .env ]; then set -a; . ./.env; set +a; fi; \
	echo "===> Building universal release APK (versionCode 1)"; \
	$(GRADLE) :app:assembleRelease
	@echo "===> $(APK_DIR)/$(APK_NAME) ready"

# Debug build + install helpers.
debug:
	$(GRADLE) :app:assembleDebug

debug-install:
	$(GRADLE) :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

install-arm64: release-arm64
	adb install -r $(APK_DIR)/$(APK_ARM64)

install-x86_64: release-x86_64
	adb install -r $(APK_DIR)/$(APK_X86_64)

install-universal: release-universal
	adb install -r $(APK_DIR)/$(APK_NAME)

# Verify APK signatures with apksigner (auto-detected from the SDK).
define verify_apk
	@if [ -z "$(BUILD_TOOLS)" ]; then \
		echo "apksigner not found (set ANDROID_HOME or ANDROID_SDK_ROOT)"; exit 1; fi; \
	echo "===> Verifying $1"; \
	$(BUILD_TOOLS) verify --verbose --print-certs $1
endef

verify-arm64: release-arm64
	$(call verify_apk,$(APK_DIR)/$(APK_ARM64))

verify-x86_64: release-x86_64
	$(call verify_apk,$(APK_DIR)/$(APK_X86_64))

verify-universal: release-universal
	$(call verify_apk,$(APK_DIR)/$(APK_NAME))

# Convenience targets.
lint:
	$(GRADLE) :app:lint

# Remove built release APKs only (keeps debug + intermediate build cache).
clean-signing:
	rm -f $(APK_DIR)/$(APK_ARM64) $(APK_DIR)/$(APK_X86_64) $(APK_DIR)/$(APK_NAME)