package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func TestUserDataDirectoryUsesLocalAppDataOnWindows(t *testing.T) {
	directory, err := userDataDirectory("windows", func(name string) string {
		if name == "LOCALAPPDATA" {
			return `C:\Users\Test\AppData\Local`
		}
		return ""
	}, func() (string, error) { return `C:\Users\Test`, nil })
	if err != nil {
		t.Fatal(err)
	}
	if directory != filepath.Clean(`C:\Users\Test\AppData\Local`) {
		t.Fatalf("unexpected directory: %s", directory)
	}
}

func TestUserDataDirectoryUsesXdgDataHomeOnLinux(t *testing.T) {
	directory, err := userDataDirectory("linux", func(name string) string {
		if name == "XDG_DATA_HOME" {
			return "/var/lib/user-data"
		}
		return ""
	}, func() (string, error) { return "/home/test", nil })
	if err != nil {
		t.Fatal(err)
	}
	if directory != "/var/lib/user-data" {
		t.Fatalf("unexpected directory: %s", directory)
	}
}

func TestUserDataDirectoryRejectsRelativeOverrides(t *testing.T) {
	directory, err := userDataDirectory("windows", func(name string) string {
		if name == "LOCALAPPDATA" {
			return "relative/app-data"
		}
		return ""
	}, func() (string, error) { return `C:\Users\Test`, nil })
	if err != nil {
		t.Fatal(err)
	}
	expected := filepath.Join(`C:\Users\Test`, "AppData", "Local")
	if directory != expected {
		t.Fatalf("unexpected fallback directory: %s", directory)
	}
}

func TestArchiveTargetRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	for _, name := range []string{"../escape", `..\escape`, "/absolute", "C:/absolute", "safe/../../escape"} {
		if _, err := archiveTarget(root, name); err == nil {
			t.Errorf("expected %q to be rejected", name)
		}
	}
}

func TestExtractZipWritesOnlyInsideDestination(t *testing.T) {
	var content bytes.Buffer
	writer := zip.NewWriter(&content)
	file, err := writer.Create("Episort/app/data.txt")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := file.Write([]byte("episort")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	destination := t.TempDir()
	if err := extractZip(destination, content.Bytes()); err != nil {
		t.Fatal(err)
	}
	actual, err := os.ReadFile(filepath.Join(destination, "Episort", "app", "data.txt"))
	if err != nil {
		t.Fatal(err)
	}
	if string(actual) != "episort" {
		t.Fatalf("unexpected content: %s", actual)
	}
}

func TestExtractZipRejectsTraversal(t *testing.T) {
	var content bytes.Buffer
	writer := zip.NewWriter(&content)
	file, err := writer.Create("../escape.txt")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = file.Write([]byte("escape"))
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := extractZip(t.TempDir(), content.Bytes()); err == nil {
		t.Fatal("expected traversal archive to be rejected")
	}
}

func TestExtractTarGzPreservesExecutableFile(t *testing.T) {
	var content bytes.Buffer
	gzipWriter := gzip.NewWriter(&content)
	tarWriter := tar.NewWriter(gzipWriter)
	body := []byte("launcher")
	if err := tarWriter.WriteHeader(&tar.Header{
		Name: "Episort/bin/Episort",
		Mode: 0o755,
		Size: int64(len(body)),
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := tarWriter.Write(body); err != nil {
		t.Fatal(err)
	}
	if err := tarWriter.Close(); err != nil {
		t.Fatal(err)
	}
	if err := gzipWriter.Close(); err != nil {
		t.Fatal(err)
	}
	destination := t.TempDir()
	if err := extractTarGz(destination, content.Bytes()); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(filepath.Join(destination, "Episort", "bin", "Episort"))
	if err != nil {
		t.Fatal(err)
	}
	if runtime.GOOS != "windows" && info.Mode().Perm()&0o111 == 0 {
		t.Fatalf("launcher is not executable: %v", info.Mode())
	}
}

func TestSafeLinkNameRejectsLinkOutsideArchiveRoot(t *testing.T) {
	if _, err := safeLinkName("Episort/runtime/link", "../../../outside"); err == nil {
		t.Fatal("expected unsafe symbolic link to be rejected")
	}
}
