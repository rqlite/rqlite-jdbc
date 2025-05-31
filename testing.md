Clear and start a new local `rqlited` instance:

    rm -rfv ~/rq-data && rqlited -fk ~/rq-data

Then run tests and measure code coverage:

    gradle clean build jacocoTestReport

Enable JDBC tracing in DBeaver. Add `-Ddbeaver.jdbc.trace=true`.

    nano ./Contents/Eclipse/dbeaver.ini

Tail DBeaver jdbc logs:

    tail -f ~/Library/DBeaverData/workspace6/.metadata/jdbc-api-trace.log
