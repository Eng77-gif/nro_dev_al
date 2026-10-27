//go:build windows

package main

import "syscall"

func init() {
	// cho cửa sổ console hiện đúng tiếng Việt
	syscall.NewLazyDLL("kernel32.dll").NewProc("SetConsoleOutputCP").Call(65001)
}
