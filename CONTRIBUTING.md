# Contributing code to nzyme

We welcome outside contributions. Here are a few things to know:

* Check with maintainers if your contribution has a chance of being merged before putting
  too much effort into it. A code change has to fit into the general product direction
  and be of high enough quality. Maintainers will be happy to discuss the proposed change
  and come up with a good approach together.
* Please include a reasonable amount of unit tests.
* The pull request will prompt you to sign the
  [Nzyme CLA](https://cla-assistant.io/nzymeorg/nzyme) and it cannot be merged
  before that is done.
* Please create a PR for each feature, bug fix or improvement. Do not mix multiple types
  of changes into a single PR. This makes reviewing and merging much easier.

# Workflow

The workflow is the same for external contributors and nzyme team members:

* All changes (except really tiny changes for team members with push access) must be performed through a pull request.
* A pull request must link to an associated issue ticket and the issue must be assigned to you (you can self-assign)
* A pull request is ready for review and merge by definition. If it is a work in progress, it must be marked as a draft.
* Commit messages should always include the associated ticket number
* The pull request branch name should start with the issue ticket number, followed by a dash (`-`) and a short but descriptive name
* Pull requests must be separated up by topic / change. Do not include multiple changes in a single pull request.
* Pull requests must be reviewed by one other nzyme team member before they can be merged.
* Pull requests must satisfy the Definition of Done before they can be merged:
  * A reasonable level of test coverage exists
  * Documentation has been updated and/or added
  * The release notes / changelog meta ticket for the corresponding release has been updated if required
