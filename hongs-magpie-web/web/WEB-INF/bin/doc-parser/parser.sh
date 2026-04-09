#!/bin/bash

CURR_PATH="$(dirname "$0")"

cd "$CURR_PATH"

uv run parser.py --file "$1"
