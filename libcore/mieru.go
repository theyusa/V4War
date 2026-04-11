package libcore

import (
	"libcore/plugin/mieruproto"
)

func DecodeMieruTrafficPattern(encoded string) (string, error) {
	jsonBytes, err := mieruproto.DecodeBase64JSON(encoded)
	if err != nil {
		return "", err
	}
	return string(jsonBytes), nil
}

func EncodeMieruTrafficPattern(jsonText string) (string, error) {
	return mieruproto.EncodeJSONBase64(jsonText)
}
