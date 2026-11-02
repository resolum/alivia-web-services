package com.resolum.alivia.operations.domain.exceptions;

import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import lombok.Getter;

import java.util.List;

/** The block ran out of technicians; {@code alternatives} are the nearest blocks that still have capacity. */
@Getter
public class SlotUnavailable extends RuntimeException {
    private final transient List<AvailableBlock> alternatives;

    public SlotUnavailable(List<AvailableBlock> alternatives) {
        super("The selected block has no technicians left. Choose one of the alternatives.");
        this.alternatives = List.copyOf(alternatives);
    }
}
