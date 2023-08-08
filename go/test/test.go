package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	stduritemplate "github.com/std-uritemplate/std-uritemplate"
)

func main() {
	templateFile := os.Args[1]
	dataFile := os.Args[2]

	data, err := readJSONFile(dataFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "File '%s' not found.\n", dataFile)
		os.Exit(1)
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
