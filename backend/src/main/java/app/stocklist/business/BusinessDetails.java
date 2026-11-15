package app.stocklist.business;

/** Normalised business profile values (already validated). */
public record BusinessDetails(String name, String address, String city, String state, String pinCode,
                              String phone, String email, String gstin) {}
