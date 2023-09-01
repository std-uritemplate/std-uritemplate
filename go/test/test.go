package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"

	stduritemplate "github.com/std-uritemplate/std-uritemplate/go"
)

func main() {
	templateFile := os.Args[1]
	dataFile := os.Args[2]

	data, err := readJSONFile(dataFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "File '%s' not found.\n", dataFile)
		os.Exit(1)
	}

	val, ok := data["nativedate"]
	if ok {
		t, err := time.Parse(time.RFC3339, val.(string))
		if err == nil {
			fmt.Fprintf(os.Stderr, "Converting to Date.\n")
			data["nativedate"] = t
		}
	}

	template, err := readFile(templateFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "File '%s' not found.\n", templateFile)
		os.Exit(1)
	}

	result, err := stduritemplate.Expand(template, data)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error occurred: '%s'.\n", err)
		fmt.Print("false\n")
	} else {
		fmt.Print(result)
	}
}

func readFile(filename string) (string, error) {
	content, err := os.ReadFile(filename)
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(content)), nil
}

func readJSONFile(filename string) (map[string]interface{}, error) {
	content, err := os.ReadFile(filename)
	if err != nil {
		return nil, err
	}

	var data map[string]interface{}
	err = json.Unmarshal(content, &data)
	if err != nil {
		return nil, err
	}

	return data, nil
}
