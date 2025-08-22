# Cloud Computing: Design and Evaluation of a Quality-of-Service Aware Virtual Machine Allocation Algorithm

This repository contains the code and data for a Master of Science research project focusing on developing an intelligent virtual machine (VM) allocation algorithm for cloud computing environments. The research addresses the challenge of balancing multiple, competing performance objectives to ensure high-quality service delivery.

-----

### 1\. Problem Statement

Existing VM allocation algorithms often fail to simultaneously optimize for key performance metrics such as **Service Level Agreement (SLA) violations**, **makespan**, and **host resource utilization**. Simple heuristic algorithms are prone to poor performance, while single-objective meta-heuristic approaches fail to address the holistic needs of dynamic cloud workloads. This work proposes a solution that can navigate these complex trade-offs.

-----

### 2\. Proposed Solution: The SLA-PSO Algorithm

The core contribution of this research is the **SLA-Based Particle Swarm Optimization (SLA-PSO)** algorithm. This is a novel, multi-objective optimization solution that integrates several critical QoS metrics into a single, unified fitness function.

The SLA-PSO algorithm is designed to:

  * **Minimize SLA Violations**: By prioritizing the allocation of VMs in a way that meets resource demands and reduces service interruptions.
  * **Reduce Makespan**: By efficiently scheduling and completing all tasks in the shortest possible time.
  * **Balance Resource Utilization**: By distributing the workload across physical hosts to prevent bottlenecks and improve overall efficiency.

-----

### 3\. Methodology

The SLA-PSO algorithm was developed and evaluated within the **CloudSim toolkit**, a well-regarded simulation framework for cloud computing research. Its performance was rigorously compared against two benchmark algorithms:

  * **Best-Fit**: A simple, greedy heuristic that places a VM on the host with the tightest resource fit.
  * **Baseline-PSO**: A standard, single-objective Particle Swarm Optimization algorithm.

The comparison was conducted under various conditions, including scaling the number of VMs and hosts, and varying the cloud landscape from homogeneous to heterogeneous environments.

-----

### 4\. Key Findings

The empirical results demonstrate that the SLA-PSO algorithm consistently and significantly outperforms both the Best-Fit and Baseline-PSO benchmarks.

  * **Superior QoS Adherence**: SLA-PSO achieved a massive reduction in SLA violations compared to the benchmarks, confirming its effectiveness in prioritizing service quality.
  * **Improved Efficiency**: The algorithm was highly effective at minimizing makespan, leading to faster task completion.
  * **Inadequacy of Benchmarks**: The research highlights the limitations of simple and single-objective algorithms, which proved ill-equipped to handle the multi-faceted demands of modern cloud environments. The high failure rate of Best-Fit and the poor QoS performance of Baseline-PSO underscore the necessity of a holistic, multi-objective approach.

-----

### 5\. Getting Started: Setting Up and Running the Code

This project includes both the core Java simulation code and a Python script for data analysis and plotting.

#### **5.1 Running the Simulation in Eclipse (Java)**

This section provides instructions for setting up the SLA-PSO project in the Eclipse IDE and executing the simulation.

**Prerequisites:**

1.  **Java Development Kit (JDK):** A recent version (JDK 8 or newer) is recommended.
2.  **Eclipse IDE for Java Developers:** Download and install the latest version from the official Eclipse website.
3.  **CloudSim Library:** The project requires the CloudSim library files, typically a `.jar` file.

**Setup Instructions:**

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/Phil254/-Cloud-Resource-Management-Thesis.git
    cd Cloud-Resource-Management-Thesis
    ```
2.  **Launch Eclipse** and select your workspace.
3.  Go to **File \> New \> Java Project**.
4.  Give your project a name (e.g., `SLA-PSO-Simulation`) and click **Finish**.
5.  Copy your `.java` files from your local repository folder and paste them into the **src** folder of your new Eclipse project.
6.  **Configure the Build Path:**
      * Right-click on your project folder in the Package Explorer and select **Properties**.
      * Go to **Java Build Path** \> **Libraries**.
      * Click **Add External JARs...** and navigate to where you have saved the CloudSim `.jar` file. Select it and click **Open**.
      * Click **Apply and Close**.

**Running the Code:**

1.  Right-click on your main class file in the `src` folder (e.g., `SlaPSOExample.java`).
2.  Select **Run As \> Java Application**.
3.  The simulation output will be displayed in the **Console** window.

#### **5.2 Analyzing the Results (Python)**

This section provides instructions for running the Python analysis script to generate plots from the simulation results.

**Prerequisites:**

1.  **Python:** A Python installation (version 3.6 or newer).
2.  **Python Libraries:** You will need `pandas`, `matplotlib`, and `seaborn`.

**Setup Instructions:**

1.  **Clone the Repository** (if you haven't already).
2.  **Install Dependencies**:
    ```bash
    pip install pandas matplotlib seaborn
    ```
3.  **Place the Data File**: Ensure the `compare_sla_results.csv` file is accessible to the script as specified in the code.

**Running the Analysis:**

1.  Execute the main analysis script, `plot_results.py`, from your terminal:
    ```bash
    python plot_results.py
    ```

The script will generate various plots (line plots, bar charts, and box plots) and save them in a new `comparison_plots` directory, visualizing the performance of each algorithm.

-----

### 6\. License

This repository is for academic purposes and is intended to support the research work detailed in the author's Master of Science thesis. All rights are reserved.
