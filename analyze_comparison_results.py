
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os

# --- Configuration ---
CSV_FILENAME = '/Users/ADMIN/Desktop/Cloudsim/cloudsim-3.0.3/cloudsim-3.0.3/compare_sla_results.csv'
OUTPUT_DIR = 'sla_pso_plots'  # Directory to save generated plots

# Ensure output directory exists
if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)
    print(f"Created output directory: {OUTPUT_DIR}")

# --- Load Data ---
try:
    df = pd.read_csv(CSV_FILENAME, sep=',')
    print(f"Successfully loaded '{CSV_FILENAME}'.")
except FileNotFoundError:
    print(f"Error: '{CSV_FILENAME}' not found. Please ensure the CSV file is in the correct directory.")
    exit()
except Exception as e:
    print(f"An error occurred while loading the CSV: {e}")
    exit()

# --- Data Cleaning and Preparation ---
# Strip whitespace from column names
df.columns = df.columns.str.strip()

# --- NEW: Print all unique algorithm names to help with debugging ---
print("\nUnique algorithms found in the 'Algorithm' column:")
if 'Algorithm' in df.columns:
    print(df['Algorithm'].unique())
else:
    print("Error: 'Algorithm' column not found in the CSV.")
    exit()

# List of columns to convert to numeric
numeric_cols = [
    'NumVMs', 'NumHosts', 'Landscape', 'ScalingFactor', 'SLA_Violations',
    'Failed_Allocations', 'Avg_ResponseTime', 'Avg_TurnaroundTime', 'Makespan',
    'Throughput', 'Avg_Host_CPU_Util(%)', 'StdDev_Host_CPU_Util(%)', 'Avg_QoS_Index'
]

for col in numeric_cols:
    if col in df.columns:
        df[col] = pd.to_numeric(df[col], errors='coerce')

# Drop rows with NaN values in critical columns and filter for SLA-PSO only
df.dropna(subset=['Algorithm', 'NumVMs', 'NumHosts', 'Landscape', 'StdDev_Host_CPU_Util(%)'], inplace=True)

# --- FIX: Change the algorithm name here if the printed list shows a different value ---
# For example, if the output is ['SLA-PSO '], you would use 'SLA-PSO '
# Or if it's ['SLA_PSO'], you would use 'SLA_PSO'
target_algorithm = 'SLA-PSO'
sla_pso_df = df[df['Algorithm'].str.strip() == target_algorithm].copy()

# Convert 'Landscape' to descriptive names for plotting
landscape_map = {1: 'Homogeneous Small', 2: 'Homogeneous Medium', 3: 'Homogeneous Large', 4: 'Heterogeneous'}
if 'Landscape' in sla_pso_df.columns:
    sla_pso_df['Landscape_Desc'] = sla_pso_df['Landscape'].map(landscape_map).fillna('Unknown')

print(f"\nData after cleaning and filtering for '{target_algorithm}'. Shape: {sla_pso_df.shape}")
print("Unique NumVMs:", sla_pso_df['NumVMs'].unique())
print("Unique NumHosts:", sla_pso_df['NumHosts'].unique())
print("Unique Landscape_Desc:", sla_pso_df['Landscape_Desc'].unique())


# --- Plotting Functions for single algorithm plots (unchanged) ---

def plot_line_single(data, x_axis, y_axis, title, filename_prefix, fixed_params_desc):
    """Generates and saves a line plot for a single algorithm."""
    plt.figure(figsize=(10, 6))
    sns.lineplot(data=data, x=x_axis, y=y_axis, marker='o', color='dodgerblue')
    plt.title(title)
    plt.xlabel(x_axis.replace('_', ' '))
    plt.ylabel(y_axis.replace('_', ' ').replace('(%)', '%'))
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.figtext(0.5, -0.05, fixed_params_desc, ha="center", fontsize=9, bbox={"facecolor":"white", "alpha":0.5, "pad":5})
    plt.tight_layout(rect=[0, 0.05, 1, 1])
    plot_path = os.path.join(OUTPUT_DIR, f"{filename_prefix}_{y_axis.replace('(', '').replace(')', '').replace('%', 'pct')}_line.png")
    plt.savefig(plot_path)
    print(f"Plot saved: {plot_path}")
    plt.close()

def plot_bar_single(data, x_axis, y_axis, title, filename_prefix, fixed_params_desc):
    """Generates and saves a bar chart for a single algorithm."""
    plt.figure(figsize=(10, 6))
    sns.barplot(data=data, x=x_axis, y=y_axis, palette=['seagreen'])
    plt.title(title)
    plt.xlabel(x_axis.replace('_', ' '))
    plt.ylabel(y_axis.replace('_', ' ').replace('(%)', '%'))
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.figtext(0.5, -0.05, fixed_params_desc, ha="center", fontsize=9, bbox={"facecolor":"white", "alpha":0.5, "pad":5})
    plt.tight_layout(rect=[0, 0.05, 1, 1])
    plot_path = os.path.join(OUTPUT_DIR, f"{filename_prefix}_{y_axis.replace('(', '').replace(')', '').replace('%', 'pct')}_bar.png")
    plt.savefig(plot_path)
    print(f"Plot saved: {plot_path}")
    plt.close()

def plot_boxplot_single(data, x_axis, y_axis, title, filename_prefix, fixed_params_desc):
    """Generates and saves a box plot for a single algorithm."""
    plt.figure(figsize=(10, 6))
    sns.boxplot(data=data, x=x_axis, y=y_axis, palette=['gold'])
    sns.stripplot(data=data, x=x_axis, y=y_axis, color='black', size=4, jitter=True, alpha=0.6)
    plt.title(title)
    plt.xlabel(x_axis.replace('_', ' '))
    plt.ylabel(y_axis.replace('_', ' ').replace('(%)', '%'))
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.figtext(0.5, -0.05, fixed_params_desc, ha="center", fontsize=9, bbox={"facecolor":"white", "alpha":0.5, "pad":5})
    plt.tight_layout(rect=[0, 0.05, 1, 1])
    plot_path = os.path.join(OUTPUT_DIR, f"{filename_prefix}_{y_axis.replace('(', '').replace(')', '').replace('%', 'pct')}_boxplot.png")
    plt.savefig(plot_path)
    print(f"Plot saved: {plot_path}")
    plt.close()


# --- Analysis and Plotting for SLA-PSO only ---

# --- 1. VM Scaling Analysis (Fixed Hosts=5, Landscape=4) ---
fixed_hosts_vm_scaling = 5
fixed_landscape_vm_scaling = 4
vm_scaling_data = sla_pso_df[(sla_pso_df['NumHosts'] == fixed_hosts_vm_scaling) & (sla_pso_df['Landscape'] == fixed_landscape_vm_scaling)].copy()

if not vm_scaling_data.empty:
    vm_scaling_grouped = vm_scaling_data.groupby(['NumVMs']).agg(
        Avg_StdDev_Host_CPU_Util_Pct=('StdDev_Host_CPU_Util(%)', 'mean')
    ).reset_index()

    fixed_desc = f"Fixed: Hosts={fixed_hosts_vm_scaling}, Landscape={landscape_map.get(fixed_landscape_vm_scaling, 'Unknown')}"
    plot_line_single(vm_scaling_grouped, 'NumVMs', 'Avg_StdDev_Host_CPU_Util_Pct',
                     'SLA-PSO: Avg StdDev of Host CPU Utilization (%) vs. Number of VMs', 'sla_pso_vm_scaling_stddev_cpu_util', fixed_desc)
else:
    print(f"\nNo data found for {target_algorithm} VM Scaling (Hosts={fixed_hosts_vm_scaling}, Landscape={fixed_landscape_vm_scaling}).")


# --- 2. Host Scaling Analysis (Fixed VMs=30, Landscape=4) ---
fixed_vms_host_scaling = 30
fixed_landscape_host_scaling = 4
host_scaling_data = sla_pso_df[(sla_pso_df['NumVMs'] == fixed_vms_host_scaling) & (sla_pso_df['Landscape'] == fixed_landscape_host_scaling)].copy()

if not host_scaling_data.empty:
    host_scaling_grouped = host_scaling_data.groupby(['NumHosts']).agg(
        Avg_StdDev_Host_CPU_Util_Pct=('StdDev_Host_CPU_Util(%)', 'mean')
    ).reset_index()

    fixed_desc = f"Fixed: VMs={fixed_vms_host_scaling}, Landscape={landscape_map.get(fixed_landscape_host_scaling, 'Unknown')}"
    plot_line_single(host_scaling_grouped, 'NumHosts', 'Avg_StdDev_Host_CPU_Util_Pct',
                     'SLA-PSO: Avg StdDev of Host CPU Utilization (%) vs. Number of Hosts', 'sla_pso_host_scaling_stddev_cpu_util', fixed_desc)
else:
    print(f"\nNo data found for {target_algorithm} Host Scaling (VMs={fixed_vms_host_scaling}, Landscape={fixed_landscape_host_scaling}).")


# --- 3. Landscape Variation Analysis (Fixed VMs=30, Hosts=5) ---
fixed_vms_landscape = 30
fixed_hosts_landscape = 5
landscape_variation_data = sla_pso_df[(sla_pso_df['NumVMs'] == fixed_vms_landscape) & (sla_pso_df['NumHosts'] == fixed_hosts_landscape)].copy()

if not landscape_variation_data.empty:
    # Ensure consistent order for landscape types on plots
    landscape_order = ['Homogeneous Small', 'Homogeneous Medium', 'Homogeneous Large', 'Heterogeneous']
    landscape_variation_data['Landscape_Desc'] = pd.Categorical(
        landscape_variation_data['Landscape_Desc'],
        categories=[l for l in landscape_order if l in landscape_variation_data['Landscape_Desc'].unique()],
        ordered=True
    )
    landscape_variation_data = landscape_variation_data.sort_values('Landscape_Desc')

    fixed_desc = f"Fixed: VMs={fixed_vms_landscape}, Hosts={fixed_hosts_landscape}"
    
    # Plotting the three requested chart types
    plot_bar_single(landscape_variation_data, 'Landscape_Desc', 'StdDev_Host_CPU_Util(%)',
                    'SLA-PSO: Avg StdDev of Host CPU Utilization (%) vs. Landscape Type', 'sla_pso_landscape_stddev_cpu_util', fixed_desc)
    
    plot_line_single(landscape_variation_data.groupby('Landscape_Desc', observed=False)['StdDev_Host_CPU_Util(%)'].mean().reset_index(),
                     'Landscape_Desc', 'StdDev_Host_CPU_Util(%)',
                     'SLA-PSO: Avg StdDev of Host CPU Utilization (%) vs. Landscape Type (Curve)', 'sla_pso_landscape_stddev_cpu_util_curve', fixed_desc)
    
    plot_boxplot_single(landscape_variation_data, 'Landscape_Desc', 'StdDev_Host_CPU_Util(%)',
                        'SLA-PSO: StdDev of Host CPU Utilization (%) Distribution by Landscape Type', 'sla_pso_landscape_stddev_cpu_util_boxplot', fixed_desc)
else:
    print(f"\nNo data found for {target_algorithm} Landscape Variation (VMs={fixed_vms_landscape}, Hosts={fixed_hosts_landscape}).")

print("\nAnalysis complete. Check the 'sla_pso_plots' directory for generated graphs.")