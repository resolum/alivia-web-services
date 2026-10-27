package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeByIdQuery;
import com.resolum.alivia.assets.domain.services.EdgeCommandService;
import com.resolum.alivia.assets.domain.services.EdgeQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateEdgeResource;
import com.resolum.alivia.assets.interfaces.rest.transform.EdgeResourceFromEntityAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.UpdateEdgeCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "api/v1/edges", produces =APPLICATION_JSON_VALUE)
@Tag(name = "Edges",description = "")
public class EdgesController {
    private final EdgeCommandService edgeCommandService;
    private final EdgeQueryService edgeQueryService;

    public EdgesController(EdgeCommandService edgeCommandService, EdgeQueryService edgeQueryService) {
        this.edgeCommandService = edgeCommandService;
        this.edgeQueryService = edgeQueryService;
    }

    @PutMapping(value = "/{edgeId}")
    @Operation(description = "")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode ="",description = ""),
                    @ApiResponse(responseCode = "",description = "" )

            }
    )
    ResponseEntity<EdgeResource>updateEdge(@PathVariable Long edgeId, @RequestBody @Valid UpdateEdgeResource resource){

       var updateEdgeCommand= UpdateEdgeCommandFromResourceAssembler.toCommandFromResource(edgeId, resource);
       edgeCommandService.handle(updateEdgeCommand);
       var getEdgeByIdQuery= edgeQueryService.handle(new GetEdgeByIdQuery(edgeId));
       if (getEdgeByIdQuery.isEmpty()){return ResponseEntity.notFound().build();}
       var edgeEntity= getEdgeByIdQuery.get();
       var edgeResource = EdgeResourceFromEntityAssembler.toResourceFromEntity(edgeEntity);
       return ResponseEntity.ok(edgeResource);
    }



}
