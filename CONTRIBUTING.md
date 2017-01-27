## Coding guidelines

Contributions to the CSync Server should use the good Scala coding style. The project is set up so that developers can use [ScalaStyle][scalastyle] to check for common violations of proper Scala coding style. ScalaStyle is set up to automatically run when the tests are run.

[scalastyle]: http://www.scalastyle.org/

## Documentation

All code changes should include comments describing the design, assumptions, dependencies, and non-obvious aspects of the implementation.
Hopefully the existing code provides a good example of appropriate code comments.
If necessary, make the appropriate updates in the README.md and other documentation files.

## Contributing your changes

1. If one does not exist already, open an issue that your contribution is going to resolve or fix.
    1. Make sure to give the issue a clear title and a very focused description.
2. On the issue page, set the appropriate Pipeline, Label(s), Milestone, and assign the issue to
yourself.
    1. We use Zenhub to organize our issues and plan our releases. Giving as much information as to
    what your changes are help us organize PRs and streamline the committing process.
3. Make a branch from the develop branch using the following naming convention:
    1. `YOUR_INITIALS/ISSUE#-DESCRIPTIVE-NAME`
    2. For example, `kb/94-create-contributingmd` was the branch that had the commit containing this
    tutorial.
4. Commit your changes!
5. When you have completed making all your changes, create a Pull Request (PR) from your git manager
or our Github repo.
6. In the comment for the PR write `Resolves #___` and fill the blank with the issue number you
created earlier.
    1. For example, the comment we wrote for the PR with this tutorial was `Resolves #94`
7. That's it, thanks for the contribution!

## Setting up your environment

You have probably got most of these set up already, but starting from scratch you will need the following:

  * SBT
  * PostgreSQL
  * RabbitMQ

1. First, install SBT using `brew install sbt`

2. Install RabbitMQ using `brew install rabbitmq`

3. Install PostGresql using `brew install postgres`

4. Start up RabbitMQ by running `brew services start rabbitmq`

5. Start up PostgreSQL by running `brew services start postgres`

6. Run `createdb` to create the PostgreSQL database for csync to use

7. You can stop RabbitMQ or PostgreSQL at any time by running `brew services stop (rabbitmq or postgres)`

## Running the tests

From the command line, run `sbt test` to run the tests and ScalaStyle checks

To generate code coverage, run `sbt clean coverage test`

To generate a human readable report, run `sbt coverageReport`. This will appear in the `core/target/{scalaversion}/scoverage-report` folder.

# Dependency Table

| Name         | Version |Author   |License | Release Date | Verification Code | URL |
|--------------|---------|---------|--------|--------------|-------------------|-----|
| vert.x framework<br>- vertx-core_3.3.0<br>- vertx-codegen_3.3.0<br>- | 3.3.0 | | Apache 2.0 | n/a | n/a |
| scala-logging | 3.4.0 | | Apache 2.0 | n/a | n/a | com.typesafe.scala-logging:scala-logging |
| amqp-client | 3.6.2 | Pivotal | Apache 2.0 | n/a | n/a | com.rabbitmq:amqp-client |
| postgresql | 9.4-1208-jdbc41 | | PostgreSQL License | n/a | n/a | org.postgresql:postgresql |
| google-api-client| 1.22.0 | Google | Apache 2.0 | n/a | n/a | com.google.api-client:google-api-client |
| HikariCP | 2.4.6 | Brett Wooldridge| Apache 2.0 | n/a | n/a | com.zaxxer:HikariCP |
| scalaj-http | 2.3.0 | Jon Hoffman | Apache 2.0 | n/a | n/a | org.scalaj:scalaj-http |
| slf4j-api | 1.7.21 | | MIT License | n/a | n/a | org.slf4j:slf4j-api |
| slf4j-simple | 1.7.21 | | MIT License | n/a | n/a | org.slf4j:slf4j-simple |
| boopickle | 1.2.5 | Otto Chrons | MIT License| n/a | n/a |  |

