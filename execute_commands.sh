#!/bin/bash

# Define the base URL
BASE_URL="http://localhost:8080/nilesh.txt"


# Execute the 'java com.gcs.cn.httpc post' commands
# java com.gcs.cn.client.httpc post "$BASE_URL?overwrite=true" -d "testing concurrency"
java com.gcs.cn.client.httpc post "$BASE_URL?overwrite=false" -d "testing concurrency01"
#
# # Execute the 'java com.gcs.cn.httpc post' commands
# java com.gcs.cn.client.httpc get -v "$BASE_URL"
# java com.gcs.cn.client.httpc post -v "$BASE_URL" -d "bleh bleh bleh"
# java com.gcs.cn.client.httpc get -v "$BASE_URL"
# # Execute the 'java com.gcs.cn.httpc get' commands
# java com.gcs.cn.client.httpc get "$BASE_URL"
# java com.gcs.cn.client.httpc get "$BASE_U

