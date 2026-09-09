//go:build windows

package main

import (
	"fmt"
	"syscall"
	"unsafe"
)

const (
	hkeyCurrentUser      = 0x80000001
	keyWrite             = 0x20006
	regOptionNonVolatile = 0
	regSz                = 1
)

type registryValue struct {
	name  string
	value string
}

// registerURLProtocol records this launcher as the handler for episort://
// links, for the current user only, so no elevation is needed and nothing
// outside the user's own profile is touched. Every launch rewrites the three
// keys: the executable moves whenever a new version is extracted, and the
// registry must follow it.
func registerURLProtocol(executable string) error {
	base := `Software\Classes\` + urlProtocolScheme
	entries := []struct {
		key    string
		values []registryValue
	}{
		{base, []registryValue{
			{"", "URL:Episort Protocol"},
			{"URL Protocol", ""},
		}},
		{base + `\DefaultIcon`, []registryValue{{"", protocolIcon(executable)}}},
		{base + `\shell\open\command`, []registryValue{{"", protocolCommand(executable)}}},
	}
	for _, entry := range entries {
		if err := writeRegistryKey(entry.key, entry.values); err != nil {
			return err
		}
	}
	return nil
}

func writeRegistryKey(subKey string, values []registryValue) error {
	advapi32 := syscall.NewLazyDLL("advapi32.dll")
	createKey := advapi32.NewProc("RegCreateKeyExW")
	setValue := advapi32.NewProc("RegSetValueExW")
	closeKey := advapi32.NewProc("RegCloseKey")

	keyPath, err := syscall.UTF16PtrFromString(subKey)
	if err != nil {
		return err
	}
	var handle syscall.Handle
	status, _, _ := createKey.Call(
		hkeyCurrentUser,
		uintptr(unsafe.Pointer(keyPath)),
		0,
		0,
		regOptionNonVolatile,
		keyWrite,
		0,
		uintptr(unsafe.Pointer(&handle)),
		0,
	)
	if status != 0 {
		return fmt.Errorf("create registry key %s: error %d", subKey, status)
	}
	defer closeKey.Call(uintptr(handle))

	for _, value := range values {
		var namePointer *uint16
		if value.name != "" {
			namePointer, err = syscall.UTF16PtrFromString(value.name)
			if err != nil {
				return err
			}
		}
		data, err := syscall.UTF16FromString(value.value)
		if err != nil {
			return err
		}
		status, _, _ = setValue.Call(
			uintptr(handle),
			uintptr(unsafe.Pointer(namePointer)),
			0,
			regSz,
			uintptr(unsafe.Pointer(&data[0])),
			uintptr(len(data)*2),
		)
		if status != 0 {
			return fmt.Errorf("write registry value %s\\%s: error %d", subKey, value.name, status)
		}
	}
	return nil
}
