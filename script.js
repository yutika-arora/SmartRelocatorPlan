const API_URL = "http://localhost:8080/api";


// =========================================
// 1. LOAD DASHBOARD STATISTICS
// =========================================

async function updateStatistics() {

    try {

        const response = await fetch(`${API_URL}/stats`);

        if (!response.ok) {
            throw new Error("Could not load dashboard statistics.");
        }

        const data = await response.json();

        document.getElementById("totalShelters").textContent =
            data.totalShelters;

        document.getElementById("availableSpaces").textContent =
            data.availableSpaces;

        document.getElementById("fullShelters").textContent =
            data.fullShelters;

    } catch (error) {

        document.getElementById("totalShelters").textContent = "--";
        document.getElementById("availableSpaces").textContent = "--";
        document.getElementById("fullShelters").textContent = "--";

        console.error("Statistics error:", error);
    }
}


// =========================================
// 2. DISPLAY SHELTER CARDS
// =========================================

function displayShelters(results) {

    const container = document.getElementById("shelterResults");

    container.replaceChildren();

    results.forEach(shelter => {

        const card = document.createElement("div");
        card.className = "shelter-card";

        const title = document.createElement("h3");
        title.textContent = shelter.name;

        card.appendChild(title);

        const details = [
            `Shelter ID: ${shelter.id}`,
            `District: ${shelter.district}`,
            `State: ${shelter.state}`,
            `Hazard Type: ${shelter.hazard}`,
            `Total Capacity: ${shelter.capacity}`,
            `People Present: ${shelter.present}`,
            `Available Spaces: ${shelter.available}`,
            `Accessibility: ${shelter.accessibility}`,
            `Medical Support: ${shelter.medical}`,
            `Data Status: ${shelter.dataStatus}`
        ];

        details.forEach(detail => {

            const paragraph = document.createElement("p");
            paragraph.textContent = detail;

            card.appendChild(paragraph);
        });

        container.appendChild(card);
    });
}


// =========================================
// 3. SEARCH SHELTERS
// =========================================

document.getElementById("searchForm").addEventListener(
    "submit",
    async function(event) {

        event.preventDefault();

        const location =
            document.getElementById("location").value.trim();

        const message =
            document.getElementById("searchMessage");

        const container =
            document.getElementById("shelterResults");

        if (!location) {

            message.textContent =
                "Please enter a district or state.";

            return;
        }

        message.textContent = "Searching shelters...";

        container.replaceChildren();

        try {

            const response = await fetch(
                `${API_URL}/shelters?location=${encodeURIComponent(location)}`
            );

            const results = await response.json();

            if (!response.ok) {
                throw new Error(
                    results.error || "Shelter search failed."
                );
            }

            if (!Array.isArray(results) || results.length === 0) {

                message.textContent =
                    `No shelters found for "${location}".`;

                return;
            }

            message.textContent =
                `Found ${results.length} matching shelter(s).`;

            displayShelters(results);

        } catch (error) {

            message.textContent =
                "Search failed: " + error.message;

            console.error("Search error:", error);
        }
    }
);


// =========================================
// 4. DISPLAY ALLOCATION SUMMARY
// =========================================

function displayAllocation(data) {

    const container =
        document.getElementById("allocationResults");

    container.replaceChildren();


    // ALLOCATION SUMMARY

    const summary = document.createElement("div");
    summary.className = "allocation-summary";

    const heading = document.createElement("h3");
    heading.textContent = "Allocation Summary";

    summary.appendChild(heading);

    const details = [
        `Requested people: ${data.requested}`,
        `People allocated: ${data.assigned}`,
        `People remaining: ${data.remaining}`,
        `Districts used: ${data.districts || "None"}`
    ];

    details.forEach(detail => {

        const paragraph = document.createElement("p");
        paragraph.textContent = detail;

        summary.appendChild(paragraph);
    });


    // SUCCESS OR WARNING MESSAGE

    const result = document.createElement("p");

    result.className = Number(data.remaining) === 0
        ? "success-message"
        : "warning-message";

    result.textContent = Number(data.remaining) === 0
        ? "All requested people have been allocated."
        : `${data.remaining} people could not be allocated because sufficient capacity was not available.`;

    summary.appendChild(result);

    container.appendChild(summary);


    // SHELTER-WISE ALLOCATION

    if (Array.isArray(data.allocations)
            && data.allocations.length > 0) {

        const allocationHeading =
            document.createElement("h3");

        allocationHeading.textContent =
            "Shelter-wise Allocation";

        container.appendChild(allocationHeading);

        data.allocations.forEach(item => {

            const card = document.createElement("div");
            card.className = "shelter-card";

            const name = document.createElement("h3");
            name.textContent = item.name;

            card.appendChild(name);

            const details = [
                `Shelter ID: ${item.id}`,
                `District: ${item.district}`,
                `People allocated: ${item.people}`
            ];

            details.forEach(detail => {

                const paragraph = document.createElement("p");
                paragraph.textContent = detail;

                card.appendChild(paragraph);
            });

            container.appendChild(card);
        });

    } else {

        const noAllocation = document.createElement("p");

        noAllocation.textContent =
            "No shelter had available capacity for this request.";

        container.appendChild(noAllocation);
    }
}


// =========================================
// 5. DISPLAY GENERATED REPORT
// =========================================

function displayReport(reportText) {

    const reportBox =
        document.getElementById("reportResults");

    reportBox.textContent =
        reportText || "No report is available.";

    reportBox.classList.add("visible");
}


// =========================================
// 6. VIEW ALLOCATION REPORT
// =========================================

document.getElementById("allocationForm").addEventListener(
    "submit",
    async function(event) {

        event.preventDefault();

        const location =
            document.getElementById("location").value.trim();

        const people =
            Number(document.getElementById("people").value);

        const message =
            document.getElementById("relocationMessage");

        const reportBox =
            document.getElementById("reportResults");

        const allocationResults =
            document.getElementById("allocationResults");

        const button =
            document.getElementById("reportButton");


        // VALIDATE LOCATION

        if (!location) {

            message.textContent =
                "Please enter the district or state in Search Shelters first.";

            document.getElementById("location").focus();

            return;
        }


        // VALIDATE NUMBER OF PEOPLE

        if (!Number.isSafeInteger(people) || people <= 0) {

            message.textContent =
                "Please enter a valid positive whole number of people.";

            return;
        }


        // CLEAR PREVIOUS RESULTS

        message.textContent =
            "Preparing allocation report...";

        allocationResults.replaceChildren();

        reportBox.textContent = "";
        reportBox.classList.remove("visible");

        button.disabled = true;


        try {

            // SEND REQUEST TO JAVA BACKEND

            const response = await fetch(
                `${API_URL}/relocate`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify({
                        location: location,
                        people: people
                    })
                }
            );

            const data = await response.json();

            if (!response.ok) {

                throw new Error(
                    data.error || "Allocation could not be completed."
                );
            }


            // DISPLAY ALLOCATION SUMMARY

            displayAllocation(data);


            // FETCH GENERATED REPORT

            const reportResponse =
                await fetch(`${API_URL}/report`);

            const reportData =
                await reportResponse.json();

            if (!reportResponse.ok) {

                throw new Error(
                    reportData.error || "Could not load the report."
                );
            }


            // DISPLAY REPORT ON WEBSITE

            displayReport(reportData.report);

            message.textContent =
                "Allocation report displayed successfully.";


            // REFRESH DASHBOARD STATISTICS

            await updateStatistics();


        } catch (error) {

            message.textContent =
                "Report error: " + error.message;

            displayReport(
                "Unable to display report: " + error.message
            );

            console.error(
                "Allocation report error:",
                error
            );

        } finally {

            button.disabled = false;
        }
    }
);


// =========================================
// 7. INITIALIZE DASHBOARD
// =========================================

updateStatistics();

console.log("Smart Relocator Plan loaded successfully.");