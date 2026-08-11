package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

//go:embed payload.bin
var payload []byte

var (
	version       = "development"
	payloadFormat = "zip"
	payloadDigest = ""
)

const extractionTimeout = 2 * time.Minute

func main() {
	err := run()
	if err == nil {
		return
	}
	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		os.Exit(exitError.ExitCode())
	}
	reportError("Episort could not start", err)
	os.Exit(1)
}

func run() error {
	digest := sha256.Sum256(payload)
	actualDigest := hex.EncodeToString(digest[:])
	if payloadDigest != "" && !strings.EqualFold(payloadDigest, actualDigest) {
		return fmt.Errorf("embedded application integrity check failed")
	}

	dataRoot, err := userDataDirectory(runtime.GOOS, os.Getenv, os.UserHomeDir)
	if err != nil {
		return err
	}
	installDirectory := filepath.Join(
		dataRoot,
		"Episort",
		"runtime",
		safeComponent(version),
		actualDigest[:16],
	)
	if err := ensureExtracted(installDirectory, actualDigest, payloadFormat, payload); err != nil {
		return err
	}
	if len(os.Args) == 2 && os.Args[1] == "--episort-portable-extract-only" {
		return nil
	}

	executable := filepath.Join(installDirectory, "Episort", "bin", "Episort")
	if runtime.GOOS == "windows" {
		executable = filepath.Join(installDirectory, "Episort", "Episort.exe")
	}
	if info, err := os.Stat(executable); err != nil || info.IsDir() {
		return fmt.Errorf("packaged application launcher is missing")
	}

	command := exec.Command(executable, os.Args[1:]...)
	command.Dir = filepath.Dir(executable)
	command.Stdin = os.Stdin
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	return command.Run()
}

func userDataDirectory(
	goos string,
	getenv func(string) string,
	homeDir func() (string, error),
) (string, error) {
	switch goos {
	case "windows":
		if localAppData := strings.TrimSpace(getenv("LOCALAPPDATA")); isAbsoluteWindowsPath(localAppData) {
			return filepath.Clean(localAppData), nil
		}
	case "linux":
		if xdgDataHome := strings.TrimSpace(getenv("XDG_DATA_HOME")); strings.HasPrefix(xdgDataHome, "/") {
			return path.Clean(xdgDataHome), nil
		}
	}
	home, err := homeDir()
	if err != nil || strings.TrimSpace(home) == "" {
		return "", fmt.Errorf("user data directory is unavailable")
	}
	if goos == "windows" {
		if !isAbsoluteWindowsPath(home) {
			return "", fmt.Errorf("user home directory is not absolute")
		}
		return filepath.Join(home, "AppData", "Local"), nil
	}
	if goos == "linux" {
		if !strings.HasPrefix(home, "/") {
			return "", fmt.Errorf("user home directory is not absolute")
		}
		return path.Join(home, ".local", "share"), nil
	}
	return filepath.Join(home, ".local", "share"), nil
}

func isAbsoluteWindowsPath(value string) bool {
	if strings.HasPrefix(value, `\\`) || strings.HasPrefix(value, `//`) {
		return true
	}
	return len(value) >= 3 && ((value[0] >= 'A' && value[0] <= 'Z') ||
		(value[0] >= 'a' && value[0] <= 'z')) && value[1] == ':' &&
		(value[2] == '\\' || value[2] == '/')
}

func safeComponent(value string) string {
	var result strings.Builder
	for _, character := range value {
		if character >= 'a' && character <= 'z' ||
			character >= 'A' && character <= 'Z' ||
			character >= '0' && character <= '9' ||
			character == '.' || character == '-' || character == '_' {
			result.WriteRune(character)
		} else {
			result.WriteRune('_')
		}
	}
	if result.Len() == 0 {
		return "unknown"
	}
	return result.String()
}

func ensureExtracted(directory, digest, format string, archive []byte) error {
	marker := filepath.Join(directory, ".complete")
	if markerMatches(marker, digest) {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(directory), 0o755); err != nil {
		return fmt.Errorf("create application data directory: %w", err)
	}

	lockPath := directory + ".lock"
	lock, err := acquireLock(lockPath, marker, digest)
	if err != nil {
		return err
	}
	if lock == nil {
		return nil
	}
	defer func() {
		_ = lock.Close()
		_ = os.Remove(lockPath)
	}()
	if markerMatches(marker, digest) {
		return nil
	}

	staging := fmt.Sprintf("%s.tmp-%d", directory, os.Getpid())
	if err := removeOwnedPath(staging, filepath.Dir(directory)); err != nil {
		return err
	}
	if err := os.MkdirAll(staging, 0o755); err != nil {
		return fmt.Errorf("create extraction directory: %w", err)
	}
	cleanup := true
	defer func() {
		if cleanup {
			_ = os.RemoveAll(staging)
		}
	}()

	switch format {
	case "zip":
		err = extractZip(staging, archive)
	case "tar.gz":
		err = extractTarGz(staging, archive)
	default:
		err = fmt.Errorf("unsupported embedded archive format %q", format)
	}
	if err != nil {
		return fmt.Errorf("extract embedded application: %w", err)
	}
	if err := os.WriteFile(filepath.Join(staging, ".complete"), []byte(digest+"\n"), 0o600); err != nil {
		return fmt.Errorf("write extraction marker: %w", err)
	}
	if err := removeOwnedPath(directory, filepath.Dir(directory)); err != nil {
		return err
	}
	if err := os.Rename(staging, directory); err != nil {
		return fmt.Errorf("activate extracted application: %w", err)
	}
	cleanup = false
	return nil
}

func acquireLock(lockPath, marker, digest string) (*os.File, error) {
	deadline := time.Now().Add(extractionTimeout)
	for {
		lock, err := os.OpenFile(lockPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
		if err == nil {
			return lock, nil
		}
		if !errors.Is(err, os.ErrExist) {
			return nil, fmt.Errorf("create extraction lock: %w", err)
		}
		if markerMatches(marker, digest) {
			return nil, nil
		}
		if info, statError := os.Stat(lockPath); statError == nil && time.Since(info.ModTime()) > extractionTimeout {
			_ = os.Remove(lockPath)
			continue
		}
		if time.Now().After(deadline) {
			return nil, fmt.Errorf("timed out waiting for another Episort launch")
		}
		time.Sleep(200 * time.Millisecond)
	}
}

func markerMatches(marker, digest string) bool {
	content, err := os.ReadFile(marker)
	return err == nil && strings.TrimSpace(string(content)) == digest
}

func removeOwnedPath(target, parent string) error {
	relative, err := filepath.Rel(parent, target)
	if err != nil || relative == "." || relative == ".." || strings.HasPrefix(relative, ".."+string(os.PathSeparator)) {
		return fmt.Errorf("refusing to remove path outside application data directory")
	}
	if err := os.RemoveAll(target); err != nil {
		return fmt.Errorf("remove incomplete application data: %w", err)
	}
	return nil
}

func extractZip(destination string, content []byte) error {
	reader, err := zip.NewReader(bytes.NewReader(content), int64(len(content)))
	if err != nil {
		return err
	}
	for _, entry := range reader.File {
		target, err := archiveTarget(destination, entry.Name)
		if err != nil {
			return err
		}
		if entry.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("zip symbolic links are not supported: %s", entry.Name)
		}
		if entry.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		input, err := entry.Open()
		if err != nil {
			return err
		}
		mode := entry.Mode().Perm()
		if mode == 0 {
			mode = 0o644
		}
		output, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
		if err != nil {
			_ = input.Close()
			return err
		}
		_, copyError := io.Copy(output, input)
		closeOutputError := output.Close()
		closeInputError := input.Close()
		if copyError != nil {
			return copyError
		}
		if closeOutputError != nil {
			return closeOutputError
		}
		if closeInputError != nil {
			return closeInputError
		}
	}
	return nil
}

func extractTarGz(destination string, content []byte) error {
	gzipReader, err := gzip.NewReader(bytes.NewReader(content))
	if err != nil {
		return err
	}
	defer gzipReader.Close()
	reader := tar.NewReader(gzipReader)
	for {
		header, err := reader.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}
		target, err := archiveTarget(destination, header.Name)
		if err != nil {
			return err
		}
		mode := os.FileMode(header.Mode).Perm()
		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, mode); err != nil {
				return err
			}
		case tar.TypeReg, tar.TypeRegA:
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return err
			}
			output, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
			if err != nil {
				return err
			}
			_, copyError := io.Copy(output, reader)
			closeError := output.Close()
			if copyError != nil {
				return copyError
			}
			if closeError != nil {
				return closeError
			}
		case tar.TypeSymlink:
			linkName, err := safeLinkName(header.Name, header.Linkname)
			if err != nil {
				return err
			}
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return err
			}
			if err := os.Symlink(filepath.FromSlash(linkName), target); err != nil {
				return err
			}
		case tar.TypeXHeader, tar.TypeXGlobalHeader:
			continue
		default:
			return fmt.Errorf("unsupported tar entry type for %s", header.Name)
		}
	}
}

func archiveTarget(root, archiveName string) (string, error) {
	normalized := strings.ReplaceAll(archiveName, "\\", "/")
	cleaned := path.Clean(normalized)
	if cleaned == "." || cleaned == ".." || strings.HasPrefix(cleaned, "../") ||
		strings.HasPrefix(cleaned, "/") || strings.ContainsRune(cleaned, '\x00') ||
		strings.Contains(strings.Split(cleaned, "/")[0], ":") {
		return "", fmt.Errorf("archive entry escapes application data directory: %q", archiveName)
	}
	target := filepath.Join(root, filepath.FromSlash(cleaned))
	relative, err := filepath.Rel(root, target)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(os.PathSeparator)) {
		return "", fmt.Errorf("archive entry escapes application data directory: %q", archiveName)
	}
	return target, nil
}

func safeLinkName(entryName, linkName string) (string, error) {
	normalizedEntry := path.Clean(strings.ReplaceAll(entryName, "\\", "/"))
	normalizedLink := strings.ReplaceAll(linkName, "\\", "/")
	resolved := path.Clean(path.Join(path.Dir(normalizedEntry), normalizedLink))
	if path.IsAbs(normalizedLink) || resolved == ".." || strings.HasPrefix(resolved, "../") {
		return "", fmt.Errorf("archive link escapes application data directory: %q", linkName)
	}
	return normalizedLink, nil
}
