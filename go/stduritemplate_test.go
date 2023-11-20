package stduritemplate

import (
	"testing"
)

func TestExpandStringArray(t *testing.T) {
	urlTemplate := "{+baseurl}/users{?statuses}"
	data := map[string]interface{}{
		"baseurl":  "https://example.com",
		"statuses": []string{"active", "pending"},
	}
	result, err := Expand(urlTemplate, data)

	if err == nil {
		t.Fail()
	}

	if "https://example.com/users?statuses=active,pending" != result {
		t.Fail()
	}
}
