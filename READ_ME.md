
This is a collaborative editing system (a google docs clone basically with some flavors of Github for version control).
#### This is all purely local setup.

#### Prerequisites
1. Install Java 17
2. Install Maven 3.6+
3. Install Node.js 16+
4. Install MySQL 8.0 (or use Docker)
5. Install Docker (optional, for databases)

#### To run the micro-services and front-end application (make sure docker desktop is running):
1. Open powershell.
2. Navigate to the main folder after cloning the repository.
3. Then type this command to ran the windows batch script: .\start-app-simple.bat

#### similarly to close the front-end application and end the micro-services' processes:
1. open powershell.
2. Navigate to the main folder after cloning the repository.
3. Then type this command to ran the windows batch script: .\stop-all-services.bat