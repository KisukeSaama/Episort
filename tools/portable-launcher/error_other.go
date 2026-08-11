//go:build !windows

package main

import (
	"fmt"
	"os"
)

func reportError(title string, err error) {
	fmt.Fprintf(os.Stderr, "%s: %v\n", title, err)
}
