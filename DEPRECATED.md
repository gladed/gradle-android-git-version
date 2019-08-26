# Deprecated APIs

Deprecated properties are supported but may be dropped in a future release. Transition your project to a supported property as soon as possible.

### ~~multiplier (int)~~ (deprecated)
Use `codeFormat` instead.

`multiplier` sets the space allowed each part of the version when calculating the version code.

For example, if you want version 1.2.3 to have a version code of 100020003 (allowing for 9999 patch increments), use `multiplier 10000`.

Use caution when increasing this value, as the maximum version code is 2100000000 (the maximum integer).

The default multiplier is `1000`.

### ~~parts (int)~~ (deprecated)
Use `codeFormat` instead.

`parts` sets the number of parts the version number will have.

For example, if you know your product will only ever use two version number parts (1.2) then use `parts 2`.

Use caution when increasing this value, as the maximum version code is 2100000000 (the maximum integer).

The default number of parts is 3.
