//go:build !windows

package main

// Linux packages are installed by the system package manager, which owns
// desktop integration there; the single-file launcher registers nothing.
func registerURLProtocol(executable string) error {
	return nil
}
