#!/bin/bash

# file directory absolute path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXAMPLE_DIR_PATH="$DIR/myaccelerator"
TARGET_DIR_PATH="$DIR/chipyard/generators/"

# create symbolic link
ln -s $EXAMPLE_DIR_PATH $TARGET_DIR_PATH
