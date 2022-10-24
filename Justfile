library := "kepler16/gx.cljc"
version := "$VERSION"
assets_dir := ".kbuild/output/github/release/assets/"
maven_server := "github-kepler"

default:
    just --list

test *ARGS:
    bin/kaocha {{ ARGS }}

clean:
    rm -rf classes
    rm -rf .cpcache
    rm -rf target

build: clean
    clojure -T:build org.corfield.build/jar :lib {{library}} :version \"{{version}}\" :transitive true
    mkdir -p {{assets_dir}}
    cp target/*.jar {{assets_dir}}

release:
    clojure -T:build org.corfield.build/deploy :repository \"{{maven_server}}\" :lib {{library}} :version \"{{version}}\"

repl *ARGS:
    bin/launchpad --emacs dev {{ ARGS }}

