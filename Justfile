library := "kepler16/gx.cljc"
version := "$VERSION"
assets_dir := ".kbuild/output/github/release/assets/"
maven_server := "github-kepler"

test:
    echo {{version}} {{library}}

default:
    just --list

build:
    clojure -T:build org.corfield.build/jar :lib {{library}} :version \"{{version}}\" :transitive true
    mkdir -p {{assets_dir}}
    cp target/*.jar {{assets_dir}}

release:
    clojure -T:build org.corfield.build/deploy :repository \"{{maven_server}}\" :lib {{library}} :version \"{{version}}\"
