package ru.wilyfox.client.highlight;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook.HighlightBlockType.*;

class BarrelHighlightSignatureTest {
    @BeforeAll
    static void initializeMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void matchesReferenceBarrelDimensions() {
        assertEquals(NORMAL_BARREL, type(flute(19, false)));
        assertEquals(NETHER_BARREL, type(flute(18, false)));
        assertEquals(END_BARREL, type(flute(17, false)));
    }

    @Test
    void detonatingBarrelsKeepTheirTypeAndDiscoveryIdentity() {
        assertEquals(type(flute(19, false)), type(flute(19, true)));
        assertEquals(type(flute(18, false)), type(flute(18, true)));
        assertEquals(type(flute(17, false)), type(flute(17, true)));
    }

    @Test
    void unrelatedNoteBlockNotesAndInstrumentsAreNotBarrels() {
        assertNull(type(flute(16, false)));
        assertNull(type(flute(21, false)));
        assertNull(type(flute(22, false)));
        assertNull(type(flute(19, false).setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.HARP)));
        assertNull(type(Blocks.STONE.defaultBlockState()));
    }

    private static BlockState flute(int note, boolean powered) {
        return Blocks.NOTE_BLOCK.defaultBlockState()
                .setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.FLUTE)
                .setValue(NoteBlock.NOTE, note)
                .setValue(NoteBlock.POWERED, powered);
    }

    private static UsefulWorldHighlightRenderHook.HighlightBlockType type(BlockState state) {
        return UsefulWorldHighlightRenderHook.HighlightBlockType.from(state, null);
    }
}
