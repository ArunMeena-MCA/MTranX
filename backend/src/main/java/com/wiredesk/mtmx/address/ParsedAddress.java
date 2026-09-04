package com.wiredesk.mtmx.address;

/**
 * Result of parsing free-text address lines through the libpostal sidecar
 * service. `confident` reflects the sidecar's OWN cross-check (country
 * component matched against a real ISO 3166 list) - not a blanket "trust
 * libpostal" flag. Fields are null when libpostal didn't extract that
 * component at all; that is a normal, expected outcome for partial/unusual
 * addresses, not an error.
 */
public class ParsedAddress {
    private String street;
    private String city;
    private String postcode;
    private String countryCode;
    private boolean confident;

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public boolean isConfident() {
        return confident;
    }

    public void setConfident(boolean confident) {
        this.confident = confident;
    }
}
