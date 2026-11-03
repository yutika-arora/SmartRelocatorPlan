public class Shelter {

    String shelterId;
    String shelterName;
    String district;
    String state;
    String hazardType;

    int totalCapacity;
    int peoplePresent;
    int availableSpaces;

    String accessibility;
    String medicalSupport;
    String dataStatus;


    public Shelter(
            String shelterId,
            String shelterName,
            String district,
            String state,
            String hazardType,
            int totalCapacity,
            int peoplePresent,
            int availableSpaces,
            String accessibility,
            String medicalSupport,
            String dataStatus) {

        this.shelterId = shelterId;
        this.shelterName = shelterName;
        this.district = district;
        this.state = state;
        this.hazardType = hazardType;
        this.totalCapacity = totalCapacity;
        this.peoplePresent = peoplePresent;
        this.availableSpaces = availableSpaces;
        this.accessibility = accessibility;
        this.medicalSupport = medicalSupport;
        this.dataStatus = dataStatus;
    }
}