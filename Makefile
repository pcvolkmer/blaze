MODULES := $(wildcard modules/*)

$(MODULES):
	$(MAKE) -C $@ $(MAKECMDGOALS)

fmt-root:
	cljfmt check

fmt: $(MODULES) fmt-root

lint-root:
	clj-kondo --lint src test deps.edn

lint: $(MODULES) lint-root

prep:
	clojure -X:deps prep

test-root: prep
	clojure -M:test:kaocha --profile :ci

test: $(MODULES) test-root

test-coverage: $(MODULES)

clean-root:
	rm -rf .clj-kondo/.cache .cpcache target

clean: $(MODULES) clean-root

build-frontend:
	$(MAKE) -C modules/frontend build

build-ingress:
	$(MAKE) -C modules/ingress all

uberjar: prep
	clojure -T:build uber

build-all: uberjar build-frontend build-ingress

outdated:
	clojure -M:outdated

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc: clean
	cloc --exclude-ext=iml,json,csv .github dev docs modules profiling scripts src test

.PHONY: $(MODULES) lint-root lint prep test-root test test-coverage clean-root \
	clean build-frontend build-ingress uberjar build-all outdated deps-tree \
	deps-list cloc
