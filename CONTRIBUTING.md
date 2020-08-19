# Contributing to DynaHist

## Found a bug?

If you find a bug/issue in the source code or a mistake in the documentation,
you can help us by
[creating an issue](https://github.com/dynatrace-oss/dynahist/issues/new/choose)
here on GitHub. Please provide an issue reproduction. Screenshots are also
helpful.

You can help the team even more and submit a pull request with a fix.

## Want a feature?

You can request a new feature also by
[creating an issue](https://github.com/dynatrace-oss/dynahist/issues/new/choose).

## Submitting a pull request

Before you submit your pull request (PR) consider the following guidelines:

- Search GitHub for an open or closed issue or PR that relates to your
  submission.
- Fork DynaHist into your namespace by using the fork button on github.
- Make your changes in a new git branch: `git checkout -b my-fix-branch master`
- Create your patch/fix/feature including appropriate tests and view the coverage of your code `build/reports/jacoco/test/html`.
- Add well documented javadoc to your changes.
- Make sure licenses and code format are up to date using `gradle licenseFormat` and `gradle googleJavaFormat`.
- Commit your changes using a descriptive commit message.
- Before pushing to Github make sure that `gradle build` runs successfully. 
- Push your branch to GitHub.
- Create a new pull request from your branch against the dynatrace-oss:master
  branch.
- If we suggest changes then:
  - Make the required updates.
  - Make sure that `gradle build` runs successfully.
  - Make sure your branch is up-to-date and includes the latest changes on master
