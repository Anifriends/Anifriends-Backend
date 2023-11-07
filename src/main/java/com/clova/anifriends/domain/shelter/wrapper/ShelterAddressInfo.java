package com.clova.anifriends.domain.shelter.wrapper;

import com.clova.anifriends.domain.shelter.exception.ShelterBadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShelterAddressInfo {

    private static final int MAX_ADDRESS_LENGTH = 100;
    private static final int MAX_ADDRESS_DETAIL_LENGTH = 100;

    @Column(name = "address")
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(name = "is_opened_address")
    private boolean isOpenedAddress;

    public ShelterAddressInfo(String address, String addressDetail, boolean isOpenedAddress) {
        validateNotNull(address, addressDetail);
        validateAddress(address);
        validateAddressDetail(addressDetail);
        this.address = address;
        this.addressDetail = addressDetail;
        this.isOpenedAddress = isOpenedAddress;
    }

    private void validateNotNull(String address, String addressDetail) {
        if (Objects.isNull(address)) {
            throw new ShelterBadRequestException("보호소 주소는 필수값입니다.");
        }
        if (Objects.isNull(addressDetail)) {
            throw new ShelterBadRequestException("보호소 상세 주소는 필수값입니다.");
        }
    }

    private void validateAddress(String address) {
        if(address.isBlank() || address.length() > MAX_ADDRESS_LENGTH) {
            throw new ShelterBadRequestException("보호소 주소는 1자 이상, 100자 이하여야 합니다.");
        }
    }

    private void validateAddressDetail(String addressDetail) {
        if(addressDetail.isBlank() || addressDetail.length() > MAX_ADDRESS_DETAIL_LENGTH) {
            throw new ShelterBadRequestException("보호소 상세 주소는 1자 이상, 100자 이하여야 합니다.");
        }
    }
}
