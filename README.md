# API Integration in a Box

The purpose of this project is create an Iterable API integration that can be run as a single 
command line tool from a customer's environment.  The basic goal here is to provide a simple
tool that a customer can use to read in a CSV file and use it to invoke Iterable API's.

## Invoking

`scala-cli -S 2.13 . -- --input-file test.csv --api-key <api-key>`

## Packaging

`scala-cli package . -o bulkUserUpdate --assembly -S 2.13`
