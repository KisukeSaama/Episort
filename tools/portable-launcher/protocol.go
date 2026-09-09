package main

// The episort:// URL scheme.
//
// A browser hands the whole URL to the registered command as its one
// argument; the launcher forwards its arguments to the packaged
// application unchanged, so the URL reaches the Java side as it was typed
// in the page. Umbra builds those links from its storage page.

const urlProtocolScheme = "episort"

// protocolCommand is the shell command Windows runs for an episort:// URL.
// The executable is quoted because it lives under the user's profile, which
// usually contains a space, and the URL placeholder is quoted so that a
// URL with an encoded space stays one argument.
func protocolCommand(executable string) string {
	return `"` + executable + `" "%1"`
}

// protocolIcon names the icon shown for the scheme: the launcher's own.
func protocolIcon(executable string) string {
	return `"` + executable + `",0`
}
