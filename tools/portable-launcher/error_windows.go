//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

func reportError(title string, err error) {
	user32 := syscall.NewLazyDLL("user32.dll")
	messageBox := user32.NewProc("MessageBoxW")
	message, _ := syscall.UTF16PtrFromString(err.Error())
	caption, _ := syscall.UTF16PtrFromString(title)
	_, _, _ = messageBox.Call(0, uintptr(unsafe.Pointer(message)), uintptr(unsafe.Pointer(caption)), 0x10)
}
