#!/bin/bash

URL="http://127.0.0.1:8080/swagger-docs"
OUTPUT_PATH="docs/openapi-full.json"

OUTPUT_DIR=$(dirname "$OUTPUT_PATH")
if [ ! -d "$OUTPUT_DIR" ]; then
    echo "Error: Directory $OUTPUT_DIR does not exist. Make sure you are running this script from the root of the project."
    exit 1
fi
echo "Downloading swagger docs from $URL..."
curl -s "$URL" -o "$OUTPUT_PATH"

if [ $? -eq 0 ]; then
    echo "Successfully downloaded and saved to $OUTPUT_PATH"
else
    echo "Error downloading the file. Make sure the server is running at $URL"
    exit 1
fi

echo "Running OpenAPI generation..."
./gradlew openApiGenerate

if [ $? -eq 0 ]; then
    echo "OpenAPI generation completed successfully."
else
    echo "Error running OpenAPI generation."
    exit 1
fi

echo "Process completed."