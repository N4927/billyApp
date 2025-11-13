.PHONY: xcgen ios-build ios-test open-xcresult clean

# ---- iOS project config (Makefile in ios/) ----
IOS_PROJ       := BillyApp.xcodeproj
IOS_SCHEME     := BillyApp
DEST_SIM      ?= platform=iOS Simulator,name=iPhone 16

# Artefatti locali per stabilità e velocità
DERIVED_DATA   := build/DerivedData
RESULT_DIR     := build
RESULT_BUNDLE  := $(RESULT_DIR)/TestResults.xcresult
RESULT_JSON    := $(RESULT_DIR)/xcresult.json

XCODEBUILD     := xcodebuild

xcgen:
	@mkdir -p $(RESULT_DIR)
	@xcodegen generate

build: xcgen
	@mkdir -p "$(DERIVED_DATA)"
	@env TMPDIR=/tmp $(XCODEBUILD) \
		-project "$(IOS_PROJ)" \
		-scheme "$(IOS_SCHEME)" \
		-sdk iphonesimulator \
		-destination '$(DEST_SIM)' \
		-derivedDataPath "$(DERIVED_DATA)" \
		build

test: xcgen
	@mkdir -p "$(RESULT_DIR)"
	@rm -rf "$(RESULT_BUNDLE)"
	@env TMPDIR=/tmp $(XCODEBUILD) \
		-project "$(IOS_PROJ)" \
		-scheme "$(IOS_SCHEME)" \
		-sdk iphonesimulator \
		-destination '$(DEST_SIM)' \
		-derivedDataPath "$(DERIVED_DATA)" \
		-resultBundlePath "$(RESULT_BUNDLE)" \
		-enableCodeCoverage YES \
		test
	@xcrun xcresulttool get --format json --path "$(RESULT_BUNDLE)" > "$(RESULT_JSON)" || true
	@echo "✔ Tests finished. xcresult: $(RESULT_BUNDLE)"

open-xcresult:
	@open "$(RESULT_BUNDLE)" || echo "⚠ Nessun result bundle: $(RESULT_BUNDLE)"

clean:
	@rm -rf "$(DERIVED_DATA)" "$(RESULT_BUNDLE)" "$(RESULT_JSON)"
	@{ test -d "$(IOS_PROJ)" && $(XCODEBUILD) -project "$(IOS_PROJ)" -scheme "$(IOS_SCHEME)" clean || true; }
