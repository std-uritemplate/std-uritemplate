package stduritemplate

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestExpandStringArray(t *testing.T) {
	urlTemplate := "{+baseurl}/users{?statuses}"
	data := map[string]interface{}{
		"baseurl":  "https://example.com",
		"statuses": []string{"active", "pending"},
	}
	result, err := Expand(urlTemplate, data)
	assert.Nil(t, err)
	assert.Equal(t, "https://example.com/users?statuses=active,pending", result)
}
