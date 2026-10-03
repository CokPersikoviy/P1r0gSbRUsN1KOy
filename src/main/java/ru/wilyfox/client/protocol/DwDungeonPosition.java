package ru.wilyfox.client.protocol;

/** Signed server map coordinates; (-1, -1) denotes an unavailable position. */
public record DwDungeonPosition(int x, int y) {
}
