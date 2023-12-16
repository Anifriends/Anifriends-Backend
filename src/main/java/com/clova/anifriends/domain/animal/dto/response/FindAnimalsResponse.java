package com.clova.anifriends.domain.animal.dto.response;

import com.clova.anifriends.domain.animal.Animal;
import com.clova.anifriends.domain.animal.repository.response.FindAnimalsResult;
import com.clova.anifriends.domain.common.PageInfo;
import java.util.List;
import org.springframework.data.domain.Slice;

public record FindAnimalsResponse(
    PageInfo pageInfo,
    List<FindAnimalResponse> animals
) {

    public record FindAnimalResponse(
        Long animalId,
        String animalName,
        String shelterName,
        String shelterAddress,
        String animalImageUrl
    ) {

        public static FindAnimalResponse from(Animal animal) {
            return new FindAnimalResponse(
                animal.getAnimalId(),
                animal.getName(),
                animal.getShelter().getName(),
                animal.getShelter().getAddress(),
                animal.getImages().get(0)
            );
        }

        public static FindAnimalResponse from(FindAnimalsResult animal) {
            return new FindAnimalResponse(
                animal.getAnimalId(),
                animal.getAnimalName(),
                animal.getShelterName(),
                animal.getShelterAddress(),
                animal.getAnimalImageUrl()
            );
        }

    }

    public static FindAnimalsResponse fromV2(Slice<FindAnimalsResult> animalsWithPagination,
        Long count) {
        PageInfo pageInfo = PageInfo.of(count, animalsWithPagination.hasNext());
        List<FindAnimalResponse> findAnimalsResponses = animalsWithPagination.get()
            .map(FindAnimalResponse::from).toList();

        return new FindAnimalsResponse(pageInfo, findAnimalsResponses);
    }
}
