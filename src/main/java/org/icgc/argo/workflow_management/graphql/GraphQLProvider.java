package org.icgc.argo.workflow_management.graphql;

import com.apollographql.federation.graphqljava.Federation;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Service
public class GraphQLProvider {
    private final MutationDataFetcher mutationDataFetcher;
    private GraphQL graphQL;
    private GraphQLSchema graphQLSchema;

    @Autowired
    public GraphQLProvider(MutationDataFetcher mutationDataFetcher) {
        this.mutationDataFetcher = mutationDataFetcher;
    }

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() throws IOException {
        URL url = Resources.getResource("schema.graphql");
        String sdl = Resources.toString(url, Charsets.UTF_8);
        graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }
    private GraphQLSchema buildSchema(String sdl) {
        return Federation.transform(sdl, buildWiring()).build();
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
               .scalar(ExtendedScalars.Json)
               .type(
                   newTypeWiring("Mutation").dataFetchers(mutationDataFetcher.mutationResolvers())
               ).build();
    }


}
